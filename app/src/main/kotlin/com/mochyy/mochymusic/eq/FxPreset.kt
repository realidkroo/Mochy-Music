package com.mochyy.mochymusic.eq

import com.mochyy.mochymusic.eq.stage.StageSettings
import kotlinx.serialization.Serializable

/** a saved set of audio fx: every effect on the page as it sounded, picked again with one tap */
@Serializable
data class FxPreset(
    val id: String,
    val name: String,
    val reverb: ReverbSettings = ReverbSettings(),
    val eightD: EightDSettings = EightDSettings(),
    val loudness: LoudnessSettings = LoudnessSettings(),
    val spatial: SpatialSettings = SpatialSettings(),
    val playback: PlaybackSettings = PlaybackSettings(),
    val volume: VolumeSettings = VolumeSettings(),
    // saved without the outputs you marked as speakers, those belong to the phone and stay when a preset is picked
    val stage: StageSettings = StageSettings(),
)
