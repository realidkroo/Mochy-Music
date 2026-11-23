package com.mochyy.mochymusic.eq

import com.mochyy.mochymusic.eq.audio.EightDParams
import com.mochyy.mochymusic.eq.audio.LoudnessParams
import com.mochyy.mochymusic.eq.audio.ReplayGainParams
import com.mochyy.mochymusic.eq.audio.ReverbParams
import com.mochyy.mochymusic.eq.audio.SpatialParams
import com.mochyy.mochymusic.eq.audio.VolumeCurve
import com.mochyy.mochymusic.eq.stage.StageNative
import com.mochyy.mochymusic.eq.stage.StageParams
import javax.inject.Inject
import javax.inject.Singleton

/** the audio fx settings every player's effects read at the start of each buffer */
@Singleton
class AudioFxService @Inject constructor() {
    @Volatile
    var eightD: EightDParams = EightDParams()

    @Volatile
    var reverb: ReverbParams = ReverbParams()

    @Volatile
    var loudness: LoudnessParams = LoudnessParams()

    @Volatile
    var spatial: SpatialParams = SpatialParams()

    /** 3d sims as the native room reads it; while it plays, spatial's own stereo modes step aside */
    @Volatile
    var stage: StageParams = StageParams.Off

    /** how loud each kind of sound in the room is right now (0..1, StageSource order), written by the playback thread for the room's dots */
    val stageLevels = FloatArray(StageNative.Kinds)

    /** replaced, never mutated: the sink notices a new curve by identity */
    @Volatile
    var volumeCurve: VolumeCurve = VolumeCurve()

    /** "even out songs": youtube's loudness numbers, or a file's own replaygain */
    @Volatile
    var normalize: Boolean = true

    /** in-app youtube loudness normalization: attenuation (<= 1) applied in the sink, boost (> 0 db) applied in LoudnessAudioProcessor; avoids attaching Android's native LoudnessEnhancer AudioEffect which breaks system Accessibility Audio Balance on Android 11 */
    @Volatile
    var trackNormalizationLinear: Float = 1f

    @Volatile
    var trackNormalizationBoostDb: Float = 0f

    fun setTrackNormalizationDb(gainDb: Float) {
        if (!gainDb.isFinite() || kotlin.math.abs(gainDb) < 0.05f) {
            trackNormalizationLinear = 1f
            trackNormalizationBoostDb = 0f
        } else if (gainDb < 0f) {
            val clamped = gainDb.coerceAtLeast(-15f)
            trackNormalizationLinear = Math.pow(10.0, clamped / 20.0).toFloat().coerceIn(0.03f, 1f)
            trackNormalizationBoostDb = 0f
        } else {
            trackNormalizationLinear = 1f
            trackNormalizationBoostDb = gainDb.coerceIn(0f, 3f)
        }
    }

    @Volatile
    var replayGain: ReplayGainParams = ReplayGainParams()

    /** how much the loudness stage is pulling peaks down right now, in db (0 or less), for the meter */
    @Volatile
    var loudnessReductionDb: Float = 0f
}
