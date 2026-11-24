package com.mochyy.mochymusic.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mochyy.mochymusic.crossmix.CrossmixAnalysisRepository
import com.mochyy.mochymusic.crossmix.CrossmixPlan
import com.mochyy.mochymusic.crossmix.CrossmixPlanner
import com.mochyy.mochymusic.crossmix.CrossmixRole
import com.mochyy.mochymusic.crossmix.CrossmixSettings
import com.mochyy.mochymusic.crossmix.CrossmixStyle
import com.mochyy.mochymusic.crossmix.CrossmixTrackInput
import com.mochyy.mochymusic.db.entities.PlaylistTransition
import com.mochyy.mochymusic.mixit.MixItLimits
import com.mochyy.mochymusic.mixit.MixItPlanner
import com.mochyy.mochymusic.playback.audio.CrossmixTransitionAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Runs Crossmix transitions: analyses the song and the next one, plans the hand-over, then plays
 * it on two players.
 *
 * The rule throughout is that a transition either happens properly or not at all. The next song
 * is prepared silently well ahead; only once it's actually ready is the outgoing side touched, and
 * if it isn't ready in time the song simply ends and the next one starts as it always did. The
 * next player starts slightly early by a measured amount and its beat is then locked to the
 * outgoing one by a small delay inside its renderer, mostly while it's still silent. Tempo is set
 * once before it starts and eased back after the overlap, never moved while both are playing,
 * because Media3 drains and resets the whole audio chain on every speed change.
 */
@UnstableApi
class CrossmixDirector(
    private val scope: CoroutineScope,
    private val host: Host,
    private val analyses: CrossmixAnalysisRepository,
) {
    interface Host {
        val currentPlayer: ExoPlayer
        fun processorFor(player: Player): CrossmixTransitionAudioProcessor?
        /** a new player holding the queue, at [index] / [positionMs], paused, at [parameters], not yet prepared */
        fun createIncoming(index: Int, positionMs: Long, parameters: PlaybackParameters): ExoPlayer
        /** [incoming] becomes the session's player; [outgoing] plays on as the fading side */
        fun promote(incoming: ExoPlayer, outgoing: ExoPlayer)
        /** the outgoing side has finished: let its player go */
        fun release(outgoing: ExoPlayer)
        /** a prepared next player that won't be used after all */
        fun discard(incoming: ExoPlayer)
        /** why Crossmix can't run right now (it's off, casting, a song-end sleep timer...), or null */
        fun crossmixBlocker(hasCustomTransition: Boolean = false): String?
        /** user-crafted Mix It transition for this pair in the active playlist, or null if unconfigured */
        fun customTransition(outMediaId: String, inMediaId: String): PlaylistTransition? = null
        fun nextIndex(): Int
        /** the song after [index] in the current play order, for pre-warming chained custom transitions */
        fun nextIndexAfter(index: Int): Int = C.INDEX_UNSET
        /** the song's lyrics, fetched if they haven't been yet; synced ones say where its voice is */
        suspend fun lyrics(item: MediaItem): String?
        /** the listener's own speed and pitch, which the transition returns to */
        fun baseParameters(): PlaybackParameters
        /** two songs are playing together (true) or the outgoing one has gone (false) */
        fun onMixingChanged(mixing: Boolean)
        /** what Crossmix is doing now, in a line, for the settings screen */
        fun onStatus(status: String)
        /** the album of the song at [index], for telling continuous albums apart */
        fun albumOf(index: Int): String?
        /** the listener's choices: overlap length, tempo matching, a style of their own, stunts */
        fun settings(): CrossmixSettings
        /** how long play() takes to be heard on this phone, remembered between launches (null until the first mix taught it) */
        fun learnedStartLatencyMs(): Double?
        fun onStartLatencyLearned(ms: Double)
    }

    private var planningJob: Job? = null
    private var planningKey: String? = null
    private var armed: ExoPlayer? = null
    private var running: Running? = null
    private val retiringJobs = HashSet<Job>()
    private val retiringPlayers = HashSet<ExoPlayer>()
    // listener's true base speed/pitch, preserved across chained transitions (T1 -> T2 -> ...)
    private var userBaseParameters: PlaybackParameters? = null
    private var lastStatus = ""
    // the test tool's jump to just before the next planned hand-over
    private var testJump = false
    private var selfSeeking = false

    // how long play() takes to be heard, learnt from each transition's first beat-lock reading and kept across launches
    private var startLatencyMs = host.learnedStartLatencyMs() ?: DefaultStartLatencyMs

    /** the outgoing song brought up to a faster next song's tempo, and the speed it goes back to if the mix is called off */
    private class SpeedRamp(val player: ExoPlayer, val base: PlaybackParameters) {
        var lastSet = base.speed
    }

    private var speedRamp: SpeedRamp? = null

    val isTransitioning: Boolean get() = running != null

    /**
     * every decision, as a line for the settings screen and for logcat. android.util.Log at info
     * level is kept in release builds (Timber is debug-only), so it can be read off a real phone.
     */
    private fun report(status: String, quietWhileMixing: Boolean = false) {
        android.util.Log.i(Tag, status)
        if (quietWhileMixing && running != null && running?.released == false) return
        if (status == lastStatus) return
        lastStatus = status
        host.onStatus(status)
    }

    private fun voiceSource(lyrics: String?, analysis: com.mochyy.mochymusic.crossmix.TrackAnalysis?): String = when {
        lyrics != null && Regex("""\[\d{1,2}:\d{2}""").containsMatchIn(lyrics) -> "synced lyrics"
        analysis?.vocal?.isNotEmpty() == true -> "the sound"
        else -> "a guess"
    }

    private fun clock(ms: Double): String {
        val total = (ms / 1_000).toLong().coerceAtLeast(0L)
        return "%d:%02d".format(total / 60, total % 60)
    }

    private class Running(
        val plan: CrossmixPlan,
        val outgoing: ExoPlayer,
        val incoming: ExoPlayer,
        val outProcessor: CrossmixTransitionAudioProcessor,
        val inProcessor: CrossmixTransitionAudioProcessor,
        val base: PlaybackParameters,
    ) {
        val jobs = ArrayList<Job>()
        var released = false
        var tempoSettled = false
        var lastSetSpeed = 0f
    }

    /** called whenever the playing song, its position or its state may have changed */
    fun schedule() {
        val activeRunning = running
        val player = host.currentPlayer
        val item = player.currentMediaItem ?: return if (activeRunning == null) cancelPending() else Unit
        val duration = player.duration
        if (duration == C.TIME_UNSET) return if (activeRunning == null) cancelPending() else Unit
        val nextIndex = host.nextIndex()
        if (nextIndex == C.INDEX_UNSET) {
            if (activeRunning == null) {
                host.crossmixBlocker(hasCustomTransition = false)?.let { report(it); return cancelPending() }
                report("Nothing queued after this song")
                cancelPending()
            }
            return
        }
        val next = player.getMediaItemAt(nextIndex)
        val custom = host.customTransition(item.mediaId, next.mediaId)
        // While a previous transition is still finishing on [player] (its incoming side), still
        // plan & pre-prepare the next track immediately for custom Mix It transitions (or for any
        // track once the old outgoing side has been released), so short chained transitions like
        // T1 (2:23 -> 3:32) -> T2 (3:36 -> 1:21) are ready before T2's cue point arrives.
        if (activeRunning != null) {
            if (activeRunning.incoming !== player) return
            if (custom == null && !activeRunning.released) return
        }
        host.crossmixBlocker(hasCustomTransition = custom != null)?.let { reason ->
            report(reason, quietWhileMixing = true)
            return cancelPending()
        }
        val minTrackMs = if (custom != null) MixItLimits.MinTrackMs else MinTrackMs
        if (duration < minTrackMs) {
            report("This song is too short to mix", quietWhileMixing = true)
            return cancelPending()
        }
        val key = "${item.mediaId}→${next.mediaId}@$nextIndex#${custom?.updatedAt ?: 0L}"
        // decided already (planned, armed, or found it can't be mixed): only a seek or a new song
        // asks again, not every pause or buffering blip
        if (key == planningKey) return
        cancelPending()
        planningKey = key
        val jump = testJump
        testJump = false
        planningJob = scope.launch { prepareTransition(player, item, next, nextIndex, duration, jump, custom) }
    }

    /** the listener moved the playhead: a planned transition is re-timed, a running one ends cleanly */
    fun onUserSeek() {
        if (selfSeeking) return
        if (running != null) abortRunning("seek") else { cancelPending(); schedule() }
    }

    /**
     * for trying a mix out without sitting through the song: plans this song's hand-over as
     * usual, then jumps to a few seconds before the next song gets ready for it
     */
    fun testMix() {
        if (running != null) abortRunning("test")
        cancelPending()
        testJump = true
        schedule()
        testJump = false
    }

    /** the song changed under us (a skip, a queue edit) */
    fun onItemChanged() {
        if (running != null) abortRunning("item changed") else cancelPending()
    }

    /** the listener changed how mixes are made: a planned one is planned again their way, a running one finishes as it is */
    fun onSettingsChanged() {
        cancelPending()
        schedule()
    }

    fun release() {
        cancelPending()
        running?.let { abortRunning("service released") }
        retiringJobs.toList().forEach { it.cancel() }
        retiringJobs.clear()
        val leftover = retiringPlayers.toList()
        retiringPlayers.clear()
        leftover.forEach { runCatching { host.release(it) } }
        userBaseParameters = null
    }

    private fun cancelPending(clearCurrentProcessor: Boolean = true) {
        planningJob?.cancel()
        planningJob = null
        planningKey = null
        restoreSpeed()
        armed?.let { incoming ->
            host.processorFor(incoming)?.clear()
            host.discard(incoming)
        }
        armed = null
        // the outgoing side is only ever armed right before the hand-over starts; if a transition
        // was called off after that, the song must play on untouched
        if (clearCurrentProcessor && running == null) {
            host.processorFor(host.currentPlayer)?.clear()
        }
        if (running == null) userBaseParameters = null
    }

    private suspend fun prepareTransition(
        outgoing: ExoPlayer,
        item: MediaItem,
        next: MediaItem,
        nextIndex: Int,
        durationMs: Long,
        testJump: Boolean,
        custom: PlaylistTransition? = null,
    ) {
        report(
            if (custom != null) "Preparing Mix It transition…" else "Listening to this song and the next one…",
            quietWhileMixing = true,
        )
        // If the track AFTER [next] also has a nearby custom transition (e.g. T1 -> T2 4 s later),
        // pre-warm its analysis in the background now so T2 never stalls on decoding when T1 promotes.
        if (custom != null) {
            val thirdIndex = host.nextIndexAfter(nextIndex)
            if (thirdIndex != C.INDEX_UNSET && thirdIndex in 0 until outgoing.mediaItemCount) {
                val third = outgoing.getMediaItemAt(thirdIndex)
                val nextCustom = host.customTransition(next.mediaId, third.mediaId)
                if (nextCustom != null && nextCustom.outStartMs - custom.inStartMs <= ChainedPreAnalyzeWindowMs) {
                    scope.launch {
                        runCatching { analyses.analyze(third, wholeTrack = nextCustom.inStartMs > 110_000L) }
                    }
                }
            }
        }

        val startedAt = System.currentTimeMillis()
        // lyrics come over the network while the audio is analysed (only needed for auto planning)
        val outLyrics = if (custom == null) scope.async { host.lyrics(item) } else null
        val inLyrics = if (custom == null) scope.async { host.lyrics(next) } else null
        val inNeedsWholeTrack = custom != null && custom.inStartMs > 110_000L
        val (outAnalysis, inAnalysis) = if (custom != null) {
            // Custom transitions must never blow past outStartMs waiting on slow network decoding:
            // read disk cache fast or bound the analysis wait by the remaining runway before outStartMs.
            val budgetMs = customAnalysisBudgetMs(
                outStartMs = custom.outStartMs,
                currentPositionMs = outgoing.currentPosition,
                speed = outgoing.playbackParameters.speed,
                testJump = testJump,
            )
            val cachedOut = analyses.cached(item.mediaId)?.takeIf { it.complete }
            val cachedIn = analyses.cached(next.mediaId)?.takeIf { !inNeedsWholeTrack || it.complete }
            if (cachedOut != null && cachedIn != null) {
                cachedOut to cachedIn
            } else {
                coroutineScope {
                    val outJob = async {
                        cachedOut
                            ?: (if (budgetMs > 0L) withTimeoutOrNull(budgetMs) { analyses.analyze(item, wholeTrack = true) } else null)
                            ?: withContext(Dispatchers.IO) { analyses.cachedFromDisk(item.mediaId) }
                    }
                    val inJob = async {
                        cachedIn
                            ?: (if (budgetMs > 0L) withTimeoutOrNull(budgetMs) { analyses.analyze(next, wholeTrack = inNeedsWholeTrack) } else null)
                            ?: withContext(Dispatchers.IO) { analyses.cachedFromDisk(next.mediaId) }
                    }
                    outJob.await() to inJob.await()
                }
            }
        } else {
            coroutineScope {
                val outJob = async { analyses.analyze(item, wholeTrack = true) }
                val inJob = async { analyses.analyze(next, wholeTrack = false) }
                outJob.await() to inJob.await()
            }
        }
        val took = (System.currentTimeMillis() - startedAt) / 1_000f
        android.util.Log.i(
            Tag,
            "analysed in %.1f s: this %s bpm (%.2f), next %s bpm (%.2f)".format(
                took, outAnalysis?.bpm?.let { "%.1f".format(it) } ?: "?", outAnalysis?.bpmConfidence ?: 0f,
                inAnalysis?.bpm?.let { "%.1f".format(it) } ?: "?", inAnalysis?.bpmConfidence ?: 0f,
            ) + (analyses.lastFailure(item.mediaId)?.let { " · this failed: $it" } ?: "") +
                (analyses.lastFailure(next.mediaId)?.let { " · next failed: $it" } ?: ""),
        )

        var plan = if (custom != null) {
            withContext(Dispatchers.Default) {
                MixItPlanner.buildPlan(
                    transition = custom,
                    outDurationMs = durationMs,
                    inDurationMs = inAnalysis?.analyzedMs?.takeIf { inAnalysis.complete } ?: 0L,
                    outAnalysis = outAnalysis,
                    inAnalysis = inAnalysis,
                )
            }
        } else {
            // an album that runs straight from one track into the next is already continuous: any
            // mix would only break it, so it plays gapless as recorded
            val album = host.albumOf(outgoing.currentMediaItemIndex)
            if (album != null && album == host.albumOf(nextIndex) && outAnalysis != null && inAnalysis != null &&
                outAnalysis.complete && outAnalysis.trailingSilenceStartMs >= durationMs - SeamlessMs &&
                inAnalysis.leadingSilenceEndMs <= SeamlessMs
            ) {
                report("Continuous album: playing straight through", quietWhileMixing = true)
                return
            }

            val outText = outLyrics?.await()
            val inText = inLyrics?.await()
            val settings = host.settings()
            android.util.Log.i(
                Tag,
                "voices from: this ${voiceSource(outText, outAnalysis)}, next ${voiceSource(inText, inAnalysis)}; settings $settings",
            )
            // a few thousand candidate mixes are scored, so off the main thread
            withContext(Dispatchers.Default) {
                CrossmixPlanner.plan(
                    outgoing = CrossmixTrackInput(item.mediaId, durationMs, outAnalysis, outText),
                    incoming = CrossmixTrackInput(next.mediaId, 0L, inAnalysis, inText),
                    settings = settings,
                )
            }
        }
        // Preserve the listener's true speed/pitch across chained transitions (T1 -> T2 -> ...)
        // instead of reading T1's temporarily beat-matched incoming speed as the user's base.
        val base = running?.base ?: userBaseParameters ?: host.baseParameters().also { userBaseParameters = it }
        if (custom != null && !testJump) {
            val prevEnd = running?.takeIf { it.incoming === outgoing }?.let { it.plan.inMediaAt(it.plan.effectiveLengthBeats) }
            plan = fitCustomPreRoll(plan, outgoing.currentPosition, prevEnd)
        }
        val settings = host.settings()
        report(
            "${plan.displayLabel} at ${clock(plan.outStartMs.toDouble())}" +
                (if (custom != null) ", Mix It" else if (settings.style != null && plan.style == settings.style) ", your pick" else "") +
                (if (plan.beatMatched) ", beat-matched" else "") +
                (if (plan.yieldAmount > 0f) ", voices kept apart" else "") +
                (if (plan.inCueMs > 1_000) ", landing on ${clock(plan.inCueMs.toDouble())} of the next" else ""),
            quietWhileMixing = true,
        )
        android.util.Log.i(
            Tag,
            "plan: %s %.1f beats from %d ms, swap on beat %.1f, beat %.1f ms, lands at %d ms, start beat %.1f, speed in %.4f out %.4f, yield %.2f".format(
                plan.displayLabel, plan.effectiveLengthBeats, plan.outStartMs, plan.effectiveSwapBeat, plan.outBeatMs, plan.inCueMs,
                plan.inStartBeat, plan.inSpeed, plan.outSpeed, plan.yieldAmount,
            ),
        )

        // the earliest moment either side does anything: the outgoing effects start at beat 0, the
        // next player may start a little before that on a silent run-up
        val firstMoveMs = plan.outMediaAt(min(plan.inStartBeat, 0f))
        // outgoing song adapts gently to Song 2's tempo over a few beats, before the next one is even prepared
        val rampMs = if (abs(plan.outSpeed - 1f) > 0.0005f) ceil(abs(plan.outSpeed - 1f) / RampStep) * plan.outBeatMs else 0.0
        if (testJump) {
            if (!stillCurrent(outgoing, item)) return
            val target = (firstMoveMs - ArmLeadMs - rampMs - TestRunUpMs).toLong().coerceAtLeast(0L)
            selfSeeking = true
            try {
                outgoing.seekTo(target)
            } finally {
                selfSeeking = false
            }
            if (!outgoing.playWhenReady) outgoing.play()
            android.util.Log.i(Tag, "test: jumped to ${clock(target.toDouble())}, the mix starts at ${clock(firstMoveMs)}")
        }
        if (custom != null) {
            if (outgoing.currentPosition > plan.outStartMs + CustomCatchUpMs) {
                report("Too close to the transition point to mix this one", quietWhileMixing = true)
                return
            }
        } else if (outgoing.currentPosition > firstMoveMs - MinArmLeadMs) {
            report("Too close to the transition point to mix this one", quietWhileMixing = true)
            return
        }
        try {
            if (rampMs > 0.0) {
                if (!waitForOutgoing(outgoing, item, (firstMoveMs - ArmLeadMs - rampMs).coerceAtLeast(0.0))) return
                host.crossmixBlocker(hasCustomTransition = custom != null)?.let { report(it, quietWhileMixing = true); return }
                // Only ramp ahead of time once any previous transition's outgoing player is gone;
                // if an earlier transition is still only idling in relaxTempo, stop its relax job first.
                val prev = running?.takeIf { it.incoming === outgoing }
                if (prev == null || prev.released) {
                    if (prev != null) {
                        running = null
                        prev.jobs.forEach { it.cancel() }
                    }
                    report(
                        if (plan.outSpeed > 1f) "Bringing this song up to the next one's tempo…"
                        else "Matching this song to the next one's tempo…",
                        quietWhileMixing = true,
                    )
                    if (!rampOutgoing(outgoing, item, plan, base, firstMoveMs - ArmLeadMs)) return
                }
            }
            prepareAndStart(outgoing, item, nextIndex, plan, base, firstMoveMs, hasCustom = custom != null)
        } finally {
            // called off at any point before the mix took this song over: it plays on at the listener's own speed
            if (running?.outgoing !== outgoing) restoreSpeed()
        }
    }

    /** brings the outgoing song smoothly to the next one's tempo in small steps a beat apart, finishing by untilMs */
    private suspend fun rampOutgoing(outgoing: ExoPlayer, item: MediaItem, plan: CrossmixPlan, base: PlaybackParameters, untilMs: Double): Boolean {
        val ramp = SpeedRamp(outgoing, base).also { speedRamp = it }
        val target = (base.speed * plan.outSpeed).coerceIn(0.5f, 2f)
        val beatsLeft = ((untilMs - outgoing.currentPosition) / plan.outBeatMs).toInt()
        val steps = min(ceil(abs(plan.outSpeed - 1f) / RampStep).toInt(), beatsLeft).coerceAtLeast(1)
        for (index in 0 until steps) {
            if (!stillCurrent(outgoing, item)) return false
            // the listener changed the speed themselves: theirs wins and this mix is off
            if (abs(outgoing.playbackParameters.speed - ramp.lastSet) > 0.0005f) {
                speedRamp = null
                return false
            }
            val speed = if (index == steps - 1) target else base.speed + (target - base.speed) * (index + 1) / steps
            outgoing.playbackParameters = PlaybackParameters(speed, base.pitch)
            ramp.lastSet = speed
            if (index < steps - 1 && !waitForOutgoing(outgoing, item, outgoing.currentPosition + plan.outBeatMs)) return false
        }
        return true
    }

    /** a sped-up outgoing song back to the listener's own speed, unless they've chosen another since */
    private fun restoreSpeed() {
        val ramp = speedRamp ?: return
        speedRamp = null
        runCatching {
            if (abs(ramp.player.playbackParameters.speed - ramp.lastSet) < 0.0005f) ramp.player.playbackParameters = ramp.base
        }
    }

    private suspend fun prepareAndStart(
        outgoing: ExoPlayer,
        item: MediaItem,
        nextIndex: Int,
        plan: CrossmixPlan,
        base: PlaybackParameters,
        firstMoveMs: Double,
        hasCustom: Boolean = false,
    ) {
        if (!waitForOutgoing(outgoing, item, (firstMoveMs - ArmLeadMs).coerceAtLeast(0.0))) return
        host.crossmixBlocker(hasCustomTransition = hasCustom)?.let { report(it, quietWhileMixing = true); return }
        if (hasCustom) {
            if (outgoing.currentPosition > plan.outStartMs + CustomCatchUpMs) {
                report("Too close to the transition point to mix this one", quietWhileMixing = true)
                return
            }
        } else if (outgoing.currentPosition > firstMoveMs - MinArmLeadMs) {
            report("Too close to the transition point to mix this one", quietWhileMixing = true)
            return
        }
        report("Getting the next song ready for ${plan.displayLabel}…", quietWhileMixing = true)

        val outProcessor = host.processorFor(outgoing)
        if (outProcessor == null || !outProcessor.canRender) {
            report("This song's audio (${outProcessor?.format ?: "no renderer"}) can't be mixed: played through", quietWhileMixing = true)
            return
        }

        // Re-fit pre-roll right before preparing incoming so it fits tightly without cutting into a running T1
        val prevForPreRoll = running?.takeIf { it.incoming === outgoing }
        val effectivePlan = if (hasCustom) {
            fitCustomPreRoll(
                plan = plan,
                currentPositionMs = outgoing.currentPosition,
                prevIncomingEndMs = prevForPreRoll?.let { it.plan.inMediaAt(it.plan.effectiveLengthBeats) },
            )
        } else {
            plan
        }
        val effectiveFirstMoveMs = effectivePlan.outMediaAt(min(effectivePlan.inStartBeat, 0f))

        // the incoming side starts on a delay with room to move both ways, plus room for any drift
        // the delay will have to absorb when the tempos are a hair apart but not stretched
        val overlapMediaMs = (effectivePlan.effectiveLengthBeats + 8) * effectivePlan.outBeatMs
        val inRate = effectivePlan.inMediaPerBeat / effectivePlan.outBeatMs
        val drift = (inRate - effectivePlan.inSpeed / effectivePlan.outSpeed) * overlapMediaMs
        val initialDelayMs = (BaseDelayMs + max(0.0, drift)).coerceAtMost(400.0)

        val parameters = PlaybackParameters((base.speed * effectivePlan.inSpeed).coerceIn(0.25f, 4f), base.pitch)
        val incoming = runCatching { host.createIncoming(nextIndex, effectivePlan.inStartMs, parameters) }.getOrNull()
        if (incoming == null) {
            report("Couldn't open a player for the next song: played through", quietWhileMixing = true)
            return
        }
        armed = incoming
        val inProcessor = host.processorFor(incoming) ?: return giveUp(incoming)
        // armed before it has decoded a thing: a paused player still fills its audio buffer, and
        // that audio is the first heard on play(), so it must already be the transition's (silent
        // before the next song's entry), never a blip of the next song at full volume
        inProcessor.arm(script(effectivePlan, CrossmixRole.Incoming, effectivePlan.inStartMs.toDouble(), 0L))
        inProcessor.setBeatLockDelay(initialDelayMs)
        var failed = false
        val errorListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { failed = true }
        }
        incoming.addListener(errorListener)
        incoming.prepare()

        // go or no-go, decided before the outgoing side is touched. READY isn't enough: a paused
        // player can be READY before its renderer has decoded anything and been set up for it
        val goDeadline = if (hasCustom) effectivePlan.outStartMs + CustomCatchUpMs else effectiveFirstMoveMs - GoMarginMs
        while (incoming.playbackState != Player.STATE_READY || !inProcessor.configured) {
            if (failed || !stillCurrent(outgoing, item) || outgoing.currentPosition > goDeadline) {
                incoming.removeListener(errorListener)
                if (stillCurrent(outgoing, item)) {
                    report(
                        when {
                            failed -> "The next song failed to load: played through"
                            incoming.playbackState == Player.STATE_READY -> "The next song's audio didn't start in time: played through"
                            else -> "The next song didn't load in time: played through"
                        },
                        quietWhileMixing = true,
                    )
                }
                return giveUp(incoming)
            }
            delay(40)
        }
        incoming.removeListener(errorListener)
        // both sides must be able to render the transition, or neither is touched
        if (inProcessor.unsupported) {
            report("The next song's audio (${inProcessor.format}) can't be mixed: played through", quietWhileMixing = true)
            return giveUp(incoming)
        }
        android.util.Log.i(Tag, "next song ready: ${inProcessor.format}, this song ${outProcessor.format}")

        // Wait until close to launch (and actively playing) BEFORE touching outProcessor or handing
        // off any previous transition T1: keeps T1 undisturbed up to T2's exact boundary and stops
        // pausing while armed from shifting outProcessor's calibration clock.
        val estimatedPlayAt = computePlayAtMs(effectivePlan, initialDelayMs, startLatencyMs, base.speed, outProcessor.currentDelayMs)
        if (!waitForOutgoing(outgoing, item, estimatedPlayAt - ArmOutBeforePlayMs, requirePlaying = true)) {
            return giveUp(incoming)
        }

        val prevSession = running?.takeIf { it.incoming === outgoing }
        val preserveChainDsp = prevSession != null
        if (prevSession != null) {
            handoffRunningForNext(prevSession, outgoing, effectivePlan, base)
        } else if (abs(effectivePlan.outSpeed - 1f) > 0.0005f) {
            val targetSpeed = (base.speed * effectivePlan.outSpeed).coerceIn(0.5f, 2f)
            if (abs(outgoing.playbackParameters.speed - targetSpeed) > 0.0005f) {
                outgoing.playbackParameters = PlaybackParameters(targetSpeed, base.pitch)
            }
        }

        val calNanos = if (outgoing.isPlaying) System.nanoTime() else 0L
        outProcessor.arm(
            script(effectivePlan, CrossmixRole.Outgoing, outgoing.currentPosition.toDouble(), calNanos),
            preserveDelayAndHistory = preserveChainDsp,
        )

        // start the next player early by its start latency and by the delay it begins with, compensated for any residual outgoing delay
        val playAt = computePlayAtMs(effectivePlan, initialDelayMs, startLatencyMs, base.speed, outProcessor.currentDelayMs)
        if (!waitForOutgoing(outgoing, item, playAt, precise = true, requirePlaying = true)) {
            // the song changed in the last moment: put everything back
            outProcessor.clear()
            return giveUp(incoming)
        }

        // If a custom transition arrived slightly after playAt (e.g. tight buffer load), trim initial delay instead of re-seeking
        val lateMs = outgoing.currentPosition - playAt
        if (hasCustom && lateMs > 5.0) {
            inProcessor.setBeatLockDelay((initialDelayMs - lateMs).coerceAtLeast(0.0))
        }

        armed = null
        val session = Running(effectivePlan, outgoing, incoming, outProcessor, inProcessor, base)
        running = session
        // the mix owns the outgoing song now: it goes when the mix is over, at whatever speed it had
        speedRamp = null
        planningJob = null
        planningKey = null

        host.promote(incoming, outgoing)
        incoming.play()
        host.onMixingChanged(true)
        report("Mixing · ${effectivePlan.displayLabel}")
        session.lastSetSpeed = incoming.playbackParameters.speed

        session.jobs += scope.launch { lockBeat(session) }
        session.jobs += scope.launch { releaseOutgoingWhenDone(session) }
        // Immediately look ahead for the next track so short chained transitions (T1 -> T2 -> T3) prepare in time
        schedule()
    }

    private fun handoffRunningForNext(
        prev: Running,
        nextOutgoing: ExoPlayer,
        nextPlan: CrossmixPlan,
        base: PlaybackParameters,
    ) {
        running = null
        prev.jobs.forEach { it.cancel() }
        if (!prev.released) {
            prev.released = true
            prev.outProcessor.fadeOutNow()
            retireOutgoing(prev.outgoing)
        }
        val targetSpeed = (base.speed * nextPlan.outSpeed).coerceIn(0.5f, 2f)
        if (abs(nextOutgoing.playbackParameters.speed - targetSpeed) > 0.0005f ||
            abs(nextOutgoing.playbackParameters.pitch - base.pitch) > 0.0005f
        ) {
            nextOutgoing.playbackParameters = PlaybackParameters(targetSpeed, base.pitch)
        }
    }

    private fun retireOutgoing(player: ExoPlayer) {
        retiringPlayers += player
        var jobRef: Job? = null
        val job = scope.launch {
            try {
                delay(90)
            } finally {
                if (retiringPlayers.remove(player)) {
                    runCatching { host.release(player) }
                }
                jobRef?.let { retiringJobs -= it }
            }
        }
        jobRef = job
        retiringJobs += job
    }

    private fun giveUp(incoming: ExoPlayer) {
        armed = null
        host.processorFor(incoming)?.clear()
        host.discard(incoming)
    }

    /**
     * keeps the next song's beat on the outgoing one for as long as they overlap: reads both
     * positions a few times a second and moves the incoming side's delay by the median error.
     * the first readings come while the next song is still silent, so the lock is in before it's heard.
     */
    private suspend fun lockBeat(session: Running) {
        val plan = session.plan
        val errors = ArrayList<Double>()
        var firstCorrection = true
        // the first reading should land inside the silent run-up, where the renderer may still jump the delay into place
        delay(100)
        while (scope.isActive && running === session) {
            val outgoing = session.outgoing
            val incoming = session.incoming
            if (session.released) break
            var fastLock = firstCorrection
            if (outgoing.isPlaying && incoming.isPlaying) {
                val outHeard = outgoing.currentPosition - session.outProcessor.currentDelayMs
                val inPos = incoming.currentPosition
                val beat = ((outHeard - plan.outStartMs) / plan.outBeatMs).toFloat()
                if (beat > plan.effectiveLengthBeats + 2f) break
                fastLock = firstCorrection || beat < 0f
                val expected = plan.inMediaAt(beat)
                val heard = inPos - session.inProcessor.currentDelayMs
                errors += heard - expected
                val window = if (fastLock) 3 else 5
                if (errors.size >= window) {
                    val median = errors.sorted()[errors.size / 2]
                    errors.clear()
                    if (abs(median) < 600.0) {
                        if (firstCorrection) {
                            // a positive error means it was heard early: next time, start it later
                            startLatencyMs = (startLatencyMs - median * 0.5).coerceIn(20.0, 250.0)
                            host.onStartLatencyLearned(startLatencyMs)
                            firstCorrection = false
                        }
                        if (abs(median) > 2.0) {
                            session.inProcessor.setBeatLockDelay(session.inProcessor.currentDelayMs + median)
                        }
                    }
                }
            } else {
                errors.clear()
            }
            delay(if (fastLock) FastLockIntervalMs else LockIntervalMs)
        }
        // the overlap is over: ease the delay away (slowly enough to be inaudible), then the tempo
        session.inProcessor.setBeatLockDelay(0.0)
        relaxTempo(session)
    }

    /** back to the listener's own tempo in steps too small to hear, once the renderer is idle */
    private suspend fun relaxTempo(session: Running) {
        val incoming = session.incoming
        val base = session.base
        // the renderer goes idle once its delay has eased back to nothing; it can't take longer
        // than this, but if it somehow does, step it out rather than hold up every later transition
        val deadline = System.currentTimeMillis() + 90_000L
        while (scope.isActive && !session.inProcessor.idle) {
            if (incoming.currentMediaItemIndex == C.INDEX_UNSET) return
            if (System.currentTimeMillis() > deadline) {
                session.inProcessor.finish()
                delay(1_000)
                break
            }
            delay(500)
        }
        while (scope.isActive) {
            val current = incoming.playbackParameters
            // the listener changed the speed themselves: theirs wins
            if (abs(current.speed - session.lastSetSpeed) > 0.0005f) break
            val gap = base.speed - current.speed
            if (abs(gap) < 0.0005f) {
                if (current.speed != base.speed || current.pitch != base.pitch) incoming.playbackParameters = base
                break
            }
            val step = gap.coerceIn(-SpeedStep, SpeedStep)
            val nextSpeed = current.speed + step
            incoming.playbackParameters = PlaybackParameters(nextSpeed, base.pitch)
            session.lastSetSpeed = nextSpeed
            // two bars between steps
            delay((8 * session.plan.inMediaPerBeat / max(0.25f, nextSpeed)).toLong().coerceIn(1_000L, 8_000L))
        }
        session.tempoSettled = true
        finishSession(session)
    }

    private suspend fun releaseOutgoingWhenDone(session: Running) {
        val plan = session.plan
        val outgoing = session.outgoing
        while (scope.isActive && running === session && !session.released) {
            val ended = outgoing.playbackState == Player.STATE_ENDED ||
                outgoing.playbackState == Player.STATE_IDLE ||
                (!outgoing.playWhenReady && outgoing.currentPosition >= plan.outStartMs + plan.effectiveLengthBeats * plan.outBeatMs)
            if (ended || outgoing.currentPosition >= plan.outReleaseMs) break
            delay(200)
        }
        if (session.released) return
        session.outProcessor.fadeOutNow()
        delay(90)
        if (session.released) return
        session.released = true
        host.release(outgoing)
        if (running === session) {
            host.onMixingChanged(false)
            letIncomingThrough(session)
            report("Mixed with ${plan.displayLabel}")
            finishSession(session)
            // Even while relaxTempo eases speed/delay back, allow planning the next track now
            if (running === session) schedule()
        }
    }

    // the old song is gone now, so a next song still held silent by its renderer (a clock that never lined up) plays on plainly instead of staying mute until a seek
    private fun letIncomingThrough(session: Running) {
        val processor = session.inProcessor
        val beat = processor.currentBeat
        if (processor.idle || (!beat.isNaN() && beat >= session.plan.effectiveSwapBeat)) return
        android.util.Log.w(Tag, "the next song was still held silent at beat $beat when the old one ended, letting it through")
        processor.finish()
    }

    /** a session is over once the outgoing player is gone and the tempo is the listener's again */
    private fun finishSession(session: Running) {
        if (running !== session) return
        if (!session.released || !session.tempoSettled) return
        running = null
        if (planningJob == null) userBaseParameters = null
        // the incoming song is the current one now; plan its own hand-over
        schedule()
    }

    private fun abortRunning(reason: String) {
        val session = running ?: return
        report("Mix stopped early ($reason)")
        running = null
        cancelPending(clearCurrentProcessor = false)
        session.jobs.forEach { it.cancel() }
        host.onMixingChanged(false)
        session.inProcessor.finish()
        if (!session.released) {
            session.outProcessor.fadeOutNow()
            session.released = true
            retireOutgoing(session.outgoing)
        }
        // whatever plays next plays at the listener's own tempo
        val current = session.incoming.playbackParameters
        if (current.speed != session.base.speed) session.incoming.playbackParameters = session.base
        userBaseParameters = null
        scope.launch { delay(150); schedule() }
    }

    private fun script(plan: CrossmixPlan, role: CrossmixRole, calibrationMs: Double, calibrationNanos: Long) =
        CrossmixTransitionAudioProcessor.Script(
            role = role,
            style = plan.style,
            variation = plan.variation,
            lengthBeats = plan.effectiveLengthBeats,
            anchorMs = if (role == CrossmixRole.Outgoing) plan.outStartMs.toDouble() else plan.inMediaAt(0f),
            mediaPerBeatMs = if (role == CrossmixRole.Outgoing) plan.outBeatMs else plan.inMediaPerBeat,
            musicalBeatMs = if (role == CrossmixRole.Outgoing) plan.outBeatMs else plan.inMediaPerBeat,
            endBeat = if (role == CrossmixRole.Outgoing) plan.effectiveLengthBeats + plan.effectiveTailBeats + 2f else plan.inSettledBeat,
            calibrationMs = calibrationMs,
            calibrationNanos = calibrationNanos,
            yieldAmount = plan.yieldAmount,
            swapBeat = plan.effectiveSwapBeat,
            mixIt = plan.mixIt,
        )

    private fun stillCurrent(player: ExoPlayer, item: MediaItem): Boolean =
        host.currentPlayer === player && player.currentMediaItem?.mediaId == item.mediaId

    /**
     * waits until the outgoing song reaches [targetMs] (its own clock, so pauses just wait longer).
     * false if the song changed or moved away meanwhile. [precise]: the last stretch is polled
     * every couple of ms, for the moment the next player starts.
     */
    private suspend fun waitForOutgoing(
        player: ExoPlayer,
        item: MediaItem,
        targetMs: Double,
        precise: Boolean = false,
        requirePlaying: Boolean = false,
    ): Boolean {
        while (scope.isActive) {
            if (!stillCurrent(player, item)) return false
            val remaining = targetMs - player.currentPosition
            if (remaining <= 0 && (!requirePlaying || player.isPlaying)) return true
            val speed = player.playbackParameters.speed.coerceAtLeast(0.25f)
            val wall = remaining / speed
            delay(
                when {
                    !player.isPlaying -> if (requirePlaying && remaining <= 0) 50L else 200L
                    wall > 3_000 -> min(1_000L, (wall - 2_000).toLong())
                    wall > 120 -> (wall / 3).toLong().coerceAtLeast(10L)
                    precise -> 2L
                    else -> wall.toLong().coerceAtLeast(1L)
                }
            )
        }
        return false
    }

    internal companion object {
        const val Tag = "Crossmix"
        const val MinTrackMs = 45_000L
        /** the next song is prepared this far ahead of its first move */
        const val ArmLeadMs = 9_000.0
        /** too close to plan properly: let this one play through */
        const val MinArmLeadMs = 2_500.0
        /** reserve before outStartMs so custom transitions never stall in analysis and miss preparing incoming */
        const val CustomAnalysisReserveMs = 4_500L
        /** minimum headroom before custom pre-roll so shrinking pre-roll fits ahead of currentPosition */
        const val CustomPreRollHeadroomMs = 600.0
        /** custom transition catch-up window after outStartMs if buffering finishes slightly late */
        const val CustomCatchUpMs = 450.0
        /** arm outProcessor just before playAt so a prior transition T1 stays untouched and pauses never skew calibration */
        const val ArmOutBeforePlayMs = 220.0
        /** pre-analyze the track after next when a chained custom transition starts within this span */
        const val ChainedPreAnalyzeWindowMs = 20_000L
        /** the next song must be ready by this long before anything happens, or it's called off */
        const val GoMarginMs = 1_200.0
        /** the next song starts this far early and is delayed back: the room the lock has to pull it later or earlier */
        const val BaseDelayMs = 60.0
        const val DefaultStartLatencyMs = 70.0
        const val FastLockIntervalMs = 50L
        const val LockIntervalMs = 100L
        const val SpeedStep = 0.004f
        /** how much the outgoing song speeds up per beat on its way to a faster next song */
        const val RampStep = 0.005f
        /** this little silence at a track boundary or less: the two run into each other */
        const val SeamlessMs = 400L
        /** the test jump lands this long before the next song is prepared */
        const val TestRunUpMs = 4_000.0

        /**
         * Shrinks a custom transition's silent pre-roll so it never starts behind [currentPositionMs]
         * and never cuts into a running previous transition's active blend window ([prevIncomingEndMs]).
         */
        fun fitCustomPreRoll(
            plan: CrossmixPlan,
            currentPositionMs: Long,
            prevIncomingEndMs: Double?,
        ): CrossmixPlan {
            val minStartFromPos = currentPositionMs.toDouble() + CustomPreRollHeadroomMs
            val earliest = if (prevIncomingEndMs != null) max(minStartFromPos, prevIncomingEndMs) else minStartFromPos
            val availPreRollMs = (plan.outStartMs.toDouble() - earliest).coerceAtLeast(0.0)
            val maxPreRollBeats = (availPreRollMs / plan.outBeatMs).toFloat()
            return plan.withMaxPreRollBeats(maxPreRollBeats)
        }

        /** Outgoing media position at which the incoming player calls play(). */
        fun computePlayAtMs(
            plan: CrossmixPlan,
            initialDelayMs: Double,
            startLatencyMs: Double,
            baseSpeed: Float,
            outDelayCompensationMs: Double,
        ): Double =
            plan.outMediaAt(plan.inStartBeat) + outDelayCompensationMs -
                initialDelayMs * plan.outSpeed / plan.inSpeed -
                startLatencyMs * baseSpeed * plan.outSpeed

        /** Maximum time a custom transition may spend decoding missing analysis before proceeding with cache/fallback. */
        fun customAnalysisBudgetMs(
            outStartMs: Long,
            currentPositionMs: Long,
            speed: Float,
            testJump: Boolean,
        ): Long {
            if (testJump) return 60_000L
            val mediaRoom = (outStartMs - currentPositionMs).coerceAtLeast(0L)
            val wallRoom = (mediaRoom / speed.coerceAtLeast(0.25f)).toLong()
            return (wallRoom - CustomAnalysisReserveMs).coerceAtLeast(0L)
        }
    }
}
