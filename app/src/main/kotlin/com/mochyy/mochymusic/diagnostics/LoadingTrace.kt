package com.mochyy.mochymusic.diagnostics

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Fixed names only: request data, media metadata and exception text cannot enter a trace. */
enum class LoadingPhase {
    APP_INITIALIZE,
    IMAGE_LOADER_INITIALIZE,
    IMAGE_CACHE_PREFERENCES,
    HOME_CACHE_RESTORE,
    HOME_INITIAL_LOCAL_READY,
    HOME_LOCAL_DATABASE,
    HOME_STARTER_SOURCE,
    HOME_DISCOVER_SOURCE,
    HOME_COMMUNITY_SOURCE,
    HOME_SIMILAR_SOURCE,
    HOME_FEED_REQUEST,
    HOME_FEED_PUBLISH,
    HOME_EXPLORE_REQUEST,
    HOME_EXPLORE_PUBLISH,
    HOME_CHARTS_REQUEST,
    HOME_CHARTS_PUBLISH,
    HOME_ARTIST_REQUEST,
    HOME_ARTIST_VIDEOS_SOURCE,
    HOME_ARTIST_VIDEOS_PUBLISH,
    HOME_CATEGORIES_SOURCE,
    HOME_CATEGORIES_REQUEST,
    HOME_GENRE_LOOKUP,
    HOME_CATEGORIES_PUBLISH,
    HOME_DISCOVER_REQUEST,
    HOME_DISCOVER_PUBLISH,
    HOME_COMMUNITY_REQUEST,
    HOME_COMMUNITY_PUBLISH,
    HOME_SIMILAR_REQUEST,
    HOME_SIMILAR_PUBLISH,
    HOME_SONG_VERSION_RESOLVE,
    HOME_ACCOUNT_REQUEST,
    HOME_ACCOUNT_INFO_REQUEST,
    HOME_ACCOUNT_PUBLISH,
    IMAGE_REQUEST,
    IMAGE_FETCH,
    IMAGE_DECODE,
}

enum class LoadingOutcome { SUCCESS, ERROR, CANCELLED }
enum class ImageOrigin { UNKNOWN, MEMORY_CACHE, MEMORY, DISK, NETWORK }

/** Pure JVM lifecycle and monotonic timing; the Android sink is installed separately. */
class LoadingTrace(
    private val enabled: Boolean,
    private val clockNanos: () -> Long,
    private val sink: Sink,
) {
    interface Sink {
        fun begin(id: Int, phase: LoadingPhase)
        fun end(completion: Completion)
    }

    data class Completion(
        val id: Int,
        val phase: LoadingPhase,
        val startedNanos: Long,
        val durationNanos: Long,
        val outcome: LoadingOutcome,
        val origin: ImageOrigin,
    )

    class Span internal constructor(
        private val trace: LoadingTrace?,
        private val id: Int,
        private val phase: LoadingPhase,
        private val startedNanos: Long,
    ) {
        private val finished = AtomicBoolean(false)

        fun finish(outcome: LoadingOutcome = LoadingOutcome.SUCCESS, origin: ImageOrigin = ImageOrigin.UNKNOWN) {
            if (trace != null && finished.compareAndSet(false, true)) {
                trace.sink.end(Completion(id, phase, startedNanos, trace.clockNanos() - startedNanos, outcome, origin))
            }
        }
    }

    private val ids = AtomicInteger()

    fun start(phase: LoadingPhase): Span {
        if (!enabled) return DISABLED_SPAN
        val id = ids.incrementAndGet()
        val startedNanos = clockNanos()
        sink.begin(id, phase)
        return Span(this, id, phase, startedNanos)
    }

    inline fun <T> measure(phase: LoadingPhase, block: () -> T): T {
        val span = start(phase)
        try {
            return block()
        } catch (e: CancellationException) {
            span.finish(LoadingOutcome.CANCELLED)
            throw e
        } catch (e: Throwable) {
            span.finish(LoadingOutcome.ERROR)
            throw e
        } finally {
            span.finish()
        }
    }

    /** Provider APIs encode failures in Result; cancellation must still leave their coroutine. */
    inline fun <T> measureResult(phase: LoadingPhase, block: () -> Result<T>): Result<T> {
        val span = start(phase)
        try {
            val result = block()
            val failure = result.exceptionOrNull()
            if (failure is CancellationException) throw failure
            span.finish(if (failure == null) LoadingOutcome.SUCCESS else LoadingOutcome.ERROR)
            return result
        } catch (e: CancellationException) {
            span.finish(LoadingOutcome.CANCELLED)
            throw e
        } catch (e: Throwable) {
            span.finish(LoadingOutcome.ERROR)
            throw e
        } finally {
            span.finish()
        }
    }

    private companion object {
        val DISABLED_SPAN = Span(null, 0, LoadingPhase.APP_INITIALIZE, 0L)
    }
}
