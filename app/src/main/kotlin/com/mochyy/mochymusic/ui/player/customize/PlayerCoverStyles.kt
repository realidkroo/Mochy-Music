// PlayerCoverStyles.kt

package com.mochyy.mochymusic.ui.player.customize

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.Preferences
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.CanvasThumbnailAnimationKey
import com.mochyy.mochymusic.constants.DiscNameKey
import com.mochyy.mochymusic.constants.DiscRealisticModeKey
import com.mochyy.mochymusic.constants.DiscRotatingAnimationKey
import com.mochyy.mochymusic.constants.PlayVideoBackgroundKey
import com.mochyy.mochymusic.constants.PlayerCoverStyle
import com.mochyy.mochymusic.constants.YtVideoBackgroundLyricsSyncKey

val PlayerCoverStyle.isDisc: Boolean
    get() = when (this) {
        PlayerCoverStyle.EDGE_TO_EDGE, PlayerCoverStyle.SQUARED -> false
        else -> true
    }

val PlayerCoverStyle.usesBoxedStage: Boolean
    get() = when (this) {
        PlayerCoverStyle.EDGE_TO_EDGE,
        PlayerCoverStyle.DISC_BIG_FULL,
        PlayerCoverStyle.DISC_BIG_LABEL,
        PlayerCoverStyle.TURNTABLE,
        -> false
        else -> true
    }

val PlayerCoverStyle.isBigDisc: Boolean
    get() = this == PlayerCoverStyle.DISC_BIG_FULL || this == PlayerCoverStyle.DISC_BIG_LABEL

val PlayerCoverStyle.hasTonearm: Boolean
    get() = this == PlayerCoverStyle.DISC_BIG_LABEL

@get:Composable
val PlayerCoverStyle.displayName: String
    get() = stringResource(
        when (this) {
            PlayerCoverStyle.EDGE_TO_EDGE -> R.string.ps_cover_edge_to_edge
            PlayerCoverStyle.SQUARED -> R.string.ps_cover_squared
            PlayerCoverStyle.DISC_SMALL_FULL -> R.string.ps_cover_disc_small_full
            PlayerCoverStyle.DISC_SMALL_LABEL -> R.string.ps_cover_disc_small_label
            PlayerCoverStyle.DISC_BIG_FULL -> R.string.ps_cover_disc_big_full
            PlayerCoverStyle.DISC_BIG_LABEL -> R.string.ps_cover_disc_big_label
            PlayerCoverStyle.DISC_ALBUM -> R.string.ps_cover_disc_album
            PlayerCoverStyle.TURNTABLE -> R.string.ps_cover_turntable
        }
    )

sealed interface CoverOption {
    val title: String
    val description: String
    @get:DrawableRes val icon: Int

    data class Switch(
        override val title: String,
        override val description: String,
        @param:DrawableRes override val icon: Int,
        val key: Preferences.Key<Boolean>,
        val default: Boolean,
        val inverted: Boolean = false,
        val parent: Switch? = null,
    ) : CoverOption

    data class Text(
        override val title: String,
        override val description: String,
        @param:DrawableRes override val icon: Int,
        val key: Preferences.Key<String>,
        val placeholder: String,
    ) : CoverOption
}

@Composable
private fun animatedCanvasOption() = CoverOption.Switch(
    title = stringResource(R.string.ps_cover_animated_canvas),
    description = stringResource(R.string.ps_cover_animated_canvas_desc),
    icon = R.drawable.sparks,
    key = CanvasThumbnailAnimationKey,
    default = true,
)

@Composable
private fun ytVideoOption() = CoverOption.Switch(
    title = stringResource(R.string.ps_cover_yt_video),
    description = stringResource(R.string.ps_cover_yt_video_desc),
    icon = R.drawable.slow_motion_video,
    key = PlayVideoBackgroundKey,
    default = false,
)

@Composable
private fun timestampMatchingOption(parent: CoverOption.Switch) = CoverOption.Switch(
    title = stringResource(R.string.ps_cover_timestamp_matching),
    description = stringResource(R.string.ps_cover_timestamp_matching_desc),
    icon = R.drawable.lyrics,
    key = YtVideoBackgroundLyricsSyncKey,
    default = true,
    parent = parent,
)

@Composable
private fun discOptions() = listOf(
    CoverOption.Switch(
        title = stringResource(R.string.ps_cover_rotating),
        description = stringResource(R.string.ps_cover_for_disc),
        icon = R.drawable.refresh,
        key = DiscRotatingAnimationKey,
        default = true,
    ),
    CoverOption.Switch(
        title = stringResource(R.string.ps_cover_realistic),
        description = stringResource(R.string.ps_cover_for_disc),
        icon = R.drawable.palette,
        key = DiscRealisticModeKey,
        default = true,
    ),
    CoverOption.Text(
        title = stringResource(R.string.ps_cover_disc_name),
        description = stringResource(R.string.ps_cover_disc_name_desc),
        icon = R.drawable.edit,
        key = DiscNameKey,
        placeholder = stringResource(R.string.ps_cover_disc_name_placeholder),
    ),
)

@get:Composable
val PlayerCoverStyle.options: List<CoverOption>
    get() = when (this) {
        PlayerCoverStyle.SQUARED -> listOf(animatedCanvasOption())
        PlayerCoverStyle.EDGE_TO_EDGE -> {
            val ytVideo = ytVideoOption()
            listOf(ytVideo, timestampMatchingOption(ytVideo), animatedCanvasOption())
        }
        else -> discOptions()
    }
