package com.mochyy.mochymusic.ui.screens.update

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.core.updater.GithubRelease
import com.mochyy.mochymusic.core.updater.downloadApk
import com.mochyy.mochymusic.core.updater.formatBytes
import com.mochyy.mochymusic.core.updater.installApk
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.player.menu.MenuSheetSurface
import kotlinx.coroutines.launch
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.sheetHandleRoom

private val CardSurface = MenuRowSurface

@Composable
fun UpdatePromptSheet(
    release: GithubRelease,
    userName: String,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onReveal: ((Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val progress = remember { mutableFloatStateOf(0f) }

    var readyToInstall by remember(release.apkName) {
        mutableStateOf(com.mochyy.mochymusic.core.updater.isDownloaded(context, release))
    }

    var needsPermission by remember { mutableStateOf(false) }
    val installPermissionMessage = stringResource(R.string.supplemental_install_permission)
    val downloadFailedMessage = stringResource(R.string.download_failed)

    val startInstall: (java.io.File) -> Unit = { file ->
        if (installApk(context, file)) {
            needsPermission = false
        } else {
            needsPermission = true
            readyToInstall = true
            error = installPermissionMessage
        }
    }

    MenuSheetSurface(
        onDismiss = onDismiss,
        modifier = modifier,
        wrapHeight = true,
        fullDetent = 0.92f,

        dismissEnabled = !downloading,
        revealProvider = onReveal,
    ) { _ ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = sheetHandleRoom())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 24.dp)
                .animateContentSize()
        ) {

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(CardSurface)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mochy_mark),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (userName.isBlank()) stringResource(R.string.update_available_title) else stringResource(R.string.supplemental_update_personal, userName),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardSurface)
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.supplemental_update_changes, release.version),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                ChangelogMarkdown(markdown = release.body)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.supplemental_update_install_target),
                color = Color(0xFF8A8A8A),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardSurface)
                    .padding(14.dp)
            ) {

                AppIconImage(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release.apkName ?: "mochy.apk",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${BuildConfig.APPLICATION_ID} · ${formatBytes(release.apkSizeBytes)}",
                        color = Color(0xFF9A9A9A),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            error?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = it, color = Color(0xFFE0736B), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(22.dp))

            val animatedProgress by animateFloatAsState(
                targetValue = progress.floatValue,
                animationSpec = tween(durationMillis = 120),
                label = "promptDownloadProgress",
            )
            PromptButton(
                enabled = !downloading && release.apkUrl != null,
                progress = animatedProgress,
                label = when {
                    downloading -> stringResource(R.string.supplemental_update_downloading, (animatedProgress * 100).toInt())
                    release.apkUrl == null -> stringResource(R.string.supplemental_update_no_apk)
                    needsPermission -> stringResource(R.string.supplemental_install_here)
                    readyToInstall -> stringResource(R.string.supplemental_install_here)
                    else -> stringResource(R.string.supplemental_install_here)
                },
                dimmed = release.apkUrl == null,
                onClick = {
                    error = null
                    val existing = com.mochyy.mochymusic.core.updater.apkFileFor(context, release)
                    if (com.mochyy.mochymusic.core.updater.isDownloaded(context, release)) {

                        startInstall(existing)
                    } else {
                        downloading = true
                        progress.floatValue = 0f
                        scope.launch {
                            val result = downloadApk(context, release) { progress.floatValue = it }
                            downloading = false
                            result.fold(
                                onSuccess = { startInstall(it) },
                                onFailure = { error = it.message ?: downloadFailedMessage },
                            )
                        }
                    }
                },
            )

            Spacer(modifier = Modifier.height(10.dp))

            PromptButton(
                enabled = !downloading,
                label = stringResource(R.string.supplemental_update_open_web),
                dimmed = downloading,
                onClick = { context.openReleaseUrl(release.htmlUrl) },
            )

            Spacer(modifier = Modifier.height(10.dp))

            PromptButton(
                enabled = !downloading,
                label = stringResource(R.string.supplemental_update_snooze),
                dimmed = downloading,
                onClick = onSnooze,
            )
        }
    }
}

@Composable
private fun PromptButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    progress: Float = 0f,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)

            .clip(RoundedCornerShape(50))
            .background(CardSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier

                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progress)
                    .background(Color(0xFF444444))
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = if (dimmed) 0.5f else 1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun android.content.Context.openReleaseUrl(url: String) {
    runCatching {
        startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
