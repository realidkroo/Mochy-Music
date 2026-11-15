// every page after the welcome screen as stateless composables, OnboardingFlow picks the page and wires the view model

package com.mochyy.mochymusic.ui.screens.setup.onboarding

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mochyy.mochymusic.ui.component.rememberDeviceTilt
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.remember
import com.mochyy.mochymusic.ui.component.BlurDirection
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.ProgressiveGlassBackground
import com.mochyy.mochymusic.ui.component.glassRoot
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.importer.account.AccountImportState
import com.mochyy.mochymusic.importer.account.AccountService
import com.mochyy.mochymusic.importer.account.RemotePlaylist
import com.mochyy.mochymusic.importer.transfer.TransferManifest
import com.mochyy.mochymusic.viewmodels.TransferStep
import com.mochyy.mochymusic.viewmodels.YouTubeSyncChoices

// ---- 1. import? ----

@Composable
internal fun ImportAskPage(onYes: () -> Unit, onSkip: () -> Unit) {
    OnboardingPage(
        badge = { PageBadge(painterResource(R.drawable.download)) },
        title = stringResource(R.string.onb_import_title),
        subtitle = stringResource(R.string.onb_import_ask_subtitle),
        footer = {
            OnbButton(stringResource(R.string.onb_yes), onClick = onYes)
            OnbButton(stringResource(R.string.onb_skip), onClick = onSkip, style = OnbButtonStyle.Secondary)
        },
    )
}

// ---- 2. which kind ----

@Composable
internal fun ImportModePage(
    onPreviousData: () -> Unit,
    onImport: () -> Unit,
    onBackup: () -> Unit,
    onBack: () -> Unit,
    backupError: String?,
) {
    OnboardingPage(
        badge = { PageBadge(painterResource(R.drawable.download)) },
        title = stringResource(R.string.onb_import_title),
        subtitle = stringResource(R.string.onb_import_mode_subtitle),
        footer = { OnbButton(stringResource(R.string.onb_go_back), onClick = onBack, style = OnbButtonStyle.Secondary) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnbChoiceCard(
                icon = painterResource(R.drawable.restore),
                title = stringResource(R.string.onb_import_previous_title),
                description = stringResource(R.string.onb_import_previous_desc),
                onClick = onPreviousData,
            )
            OnbChoiceCard(
                icon = painterResource(R.drawable.playlist_add),
                title = stringResource(R.string.onb_import),
                description = stringResource(R.string.onb_import_desc),
                onClick = onImport,
            )
            OnbChoiceCard(
                icon = painterResource(R.drawable.backup),
                title = stringResource(R.string.onb_import_backup_title),
                description = stringResource(R.string.onb_import_backup_desc),
                onClick = onBackup,
            )
            if (backupError != null) {
                Text(backupError, color = Color(0xFFFF8A80), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---- 2b. bringing previous data over ----

@Composable
internal fun RestoringPage(step: TransferStep, manifest: TransferManifest?) {
    val done = step is TransferStep.Done
    val progress = if (done) 1f else (step as? TransferStep.Working)?.progress ?: 0f
    OnboardingPage(
        badge = { PageBadge(painterResource(if (done) R.drawable.check else R.drawable.restore)) },
        title = if (done) stringResource(R.string.onb_all_set) else stringResource(R.string.onb_bringing_over),
        subtitle = when {
            done -> stringResource(R.string.onb_restart_once)
            manifest != null -> stringResource(
                R.string.onb_restore_counts,
                pluralStringResource(R.plurals.onb_songs, manifest.songs, manifest.songs),
                pluralStringResource(R.plurals.onb_playlists, manifest.playlists, manifest.playlists),
                pluralStringResource(R.plurals.onb_plays, manifest.plays, manifest.plays),
            )
            else -> stringResource(R.string.onb_reading_export)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LinearProgressIndicator(
                progress = { progress },
                color = Color.White,
                trackColor = Onb.Card,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
            )
            Text(
                if (done) stringResource(R.string.onb_restarting) else stringResource(R.string.onb_restore_progress, (progress * 100).toInt()),
                color = Onb.Subtitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---- 3. provider ----

@Composable
internal fun ProviderListPage(onPick: (AccountService) -> Unit, onBack: () -> Unit) {
    OnboardingPage(
        badge = { PageBadge(painterResource(R.drawable.library_music)) },
        title = stringResource(R.string.onb_select_provider),
        footer = { OnbButton(stringResource(R.string.onb_go_back), onClick = onBack, style = OnbButtonStyle.Secondary) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                AccountService.SPOTIFY to stringResource(R.string.onb_provider_spotify_desc),
                AccountService.APPLE_MUSIC to stringResource(R.string.onb_provider_apple_desc),
                AccountService.YOUTUBE_MUSIC to stringResource(R.string.onb_provider_youtube_desc),
                AccountService.TIDAL to stringResource(R.string.onb_provider_tidal_desc),
            ).forEach { (service, description) ->
                OnbCard(modifier = Modifier.fillMaxWidth(), onClick = { onPick(service) }, shape = RoundedCornerShape(20.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProviderSquircle(service, size = 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(service.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(description, color = Onb.Muted, fontSize = 12.sp, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

// ---- 4. setup (instructions, the browser sheet opens over this) ----

@Composable
internal fun ProviderSetupPage(
    service: AccountService,
    state: AccountImportState?,
    browserOpen: Boolean,
    onOpenBrowser: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    OnboardingPage(
        badge = { ProviderSquircle(service, size = 40.dp) },
        title = stringResource(R.string.onb_provider_setup_title, service.label),
        subtitle = stringResource(R.string.onb_provider_setup_subtitle),
        footer = {
            when (state) {
                is AccountImportState.Failed -> OnbButton(
                    if (state.signedOut) stringResource(R.string.onb_sign_in_again) else stringResource(R.string.onb_try_again),
                    onClick = onRetry,
                )
                is AccountImportState.Loading, is AccountImportState.Choosing -> Unit
                else -> if (!browserOpen) OnbButton(stringResource(R.string.onb_open_sign_in), onClick = onOpenBrowser)
            }
            OnbButton(stringResource(R.string.onb_go_back), onClick = onBack, style = OnbButtonStyle.Secondary)
        },
    ) {
        when (state) {
            is AccountImportState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(12.dp))
                Text(state.label, color = Onb.Subtitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            is AccountImportState.Failed -> Text(state.message, color = Color(0xFFFF8A80), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            else -> Text(
                stringResource(R.string.onb_provider_setup_body, service.label),
                color = Onb.Muted,
                fontSize = 14.sp,
            )
        }
    }
}

// ---- 5. what to sync or import ----

@Composable
internal fun WhatToSyncPage(
    service: AccountService,
    state: AccountImportState?,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onConfirm: () -> Unit,
    onCancelReading: () -> Unit,
    onBack: () -> Unit,
) {
    val choosing = state as? AccountImportState.Choosing
    val reading = state as? AccountImportState.Reading
    val selectedCount = choosing?.selected?.size ?: 0
    OnboardingPage(
        badge = { ProviderSquircle(service, size = 40.dp) },
        title = stringResource(R.string.onb_what_to_import),
        subtitle = choosing?.accountName?.let { stringResource(R.string.onb_signed_in_as, it) },
        scrollable = false,
        footer = {
            if (reading != null) {
                OnbButton(stringResource(R.string.onb_stop), onClick = onCancelReading, style = OnbButtonStyle.Secondary)
            } else {
                OnbButton(
                    text = when {
                        selectedCount == 0 -> stringResource(R.string.onb_choose_what_to_import)
                        service == AccountService.YOUTUBE_MUSIC -> stringResource(R.string.onb_next)
                        else -> stringResource(R.string.onb_import_count, selectedCount)
                    },
                    enabled = selectedCount > 0,
                    onClick = onConfirm,
                )
                OnbButton(stringResource(R.string.onb_go_back), onClick = onBack, style = OnbButtonStyle.Secondary)
            }
        },
    ) {
        if (reading != null) {
            Text(stringResource(R.string.onb_reading_progress, reading.label, reading.done + 1, reading.total), color = Onb.Subtitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { reading.done.toFloat() / reading.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Onb.Card,
            )
            return@OnboardingPage
        }
        if (choosing == null) return@OnboardingPage
        val allSelected = selectedCount == choosing.playlists.size && selectedCount > 0
        Text(
            text = if (allSelected) stringResource(R.string.onb_select_none) else stringResource(R.string.onb_select_all),
            color = Onb.Subtitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = if (allSelected) onSelectNone else onSelectAll)
                .padding(vertical = 6.dp),
        )
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val columns = if (maxWidth >= 480.dp) 3 else 2
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (choosing.playlists.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.onb_nothing_in_account), color = Onb.Muted, fontSize = 14.sp)
                    }
                }
                items(choosing.playlists, key = { it.id }) { playlist ->
                    CoverTile(playlist = playlist, selected = playlist.id in choosing.selected, onClick = { onToggle(playlist.id) })
                }
            }
        }
    }
}

@Composable
private fun CoverTile(playlist: RemotePlaylist, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    val s = if (selected) 0.94f else 1f
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(20.dp))
                .background(Onb.Card)
                .then(if (selected) Modifier.border(3.dp, Color.White, RoundedCornerShape(20.dp)) else Modifier),
        ) {
            when {
                playlist.isLiked -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF5B5B66), Color(0xFF1F1F24)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.favorite), contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
                playlist.coverUrl != null -> AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        playlist.name.take(1).uppercase(),
                        color = Onb.Muted,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.check), contentDescription = stringResource(R.string.onb_selected), tint = Color.Black, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val songCount = playlist.trackCount?.let { pluralStringResource(R.plurals.onb_songs, it, it) }
        val detail = listOfNotNull(songCount, playlist.subtitle).joinToString(" · ")
        if (detail.isNotEmpty()) Text(detail, color = Onb.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- 6. youtube only: also keep it in sync? ----

@Composable
internal fun SyncOptionsPage(
    choices: YouTubeSyncChoices,
    onChange: (YouTubeSyncChoices) -> Unit,
    reading: AccountImportState.Reading?,
    onSync: () -> Unit,
    onJustImport: () -> Unit,
    onBack: () -> Unit,
) {
    OnboardingPage(
        badge = { ProviderSquircle(AccountService.YOUTUBE_MUSIC, size = 40.dp) },
        title = stringResource(R.string.onb_sync_title),
        subtitle = stringResource(R.string.onb_sync_subtitle),
        footer = {
            if (reading != null) {
                Text(
                    stringResource(R.string.onb_reading_progress, reading.label, reading.done + 1, reading.total),
                    color = Onb.Subtitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                OnbButton(stringResource(R.string.onb_import_and_sync), onClick = onSync)
                OnbButton(stringResource(R.string.onb_just_import), onClick = onJustImport, style = OnbButtonStyle.Secondary)
                OnbButton(stringResource(R.string.onb_go_back), onClick = onBack, style = OnbButtonStyle.Secondary)
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OnbToggleCard(
                title = stringResource(R.string.onb_sync_launch_title),
                description = stringResource(R.string.onb_sync_launch_desc),
                checked = choices.syncOnLaunch,
                onCheckedChange = { onChange(choices.copy(syncOnLaunch = it)) },
            )
            OnbToggleCard(
                title = stringResource(R.string.onb_sync_new_playlists_title),
                description = stringResource(R.string.onb_sync_new_playlists_desc),
                checked = choices.newPlaylistsToYouTube,
                onCheckedChange = { onChange(choices.copy(newPlaylistsToYouTube = it)) },
            )
            OnbToggleCard(
                title = stringResource(R.string.onb_sync_push_plays_title),
                description = stringResource(R.string.onb_sync_push_plays_desc),
                checked = choices.reportPlays,
                onCheckedChange = { onChange(choices.copy(reportPlays = it)) },
            )
            OnbToggleCard(
                title = stringResource(R.string.onb_sync_bring_history_title),
                description = stringResource(R.string.onb_sync_bring_history_desc),
                checked = choices.copyHistory,
                onCheckedChange = { onChange(choices.copy(copyHistory = it)) },
            )
        }
    }
}

// ---- 7. profile ----

@Composable
internal fun ProfilePage(
    username: String,
    onUsernameChange: (String) -> Unit,
    photo: Any?,
    onPhotoClick: () -> Unit,
    cardNumber: String,
    joinedText: String,
    onNext: () -> Unit,
) {
    OnboardingPage(
        badge = { PageBadge(painterResource(R.drawable.person)) },
        title = stringResource(R.string.onb_profile_title),
        footer = {
            Text(
                stringResource(R.string.onb_never_store),
                color = Onb.Faint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
            OnbButton(stringResource(R.string.onb_next), onClick = onNext, enabled = username.isNotBlank())
        },
    ) {
        Text(stringResource(R.string.onb_username_prompt), color = Onb.Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        OnbTextField(value = username, onValueChange = { onUsernameChange(it.take(32)) }, placeholder = stringResource(R.string.onb_username_hint))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.onb_set_photo), color = Onb.Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        val deviceTilt = rememberDeviceTilt()
        ProfileIdCard(
            username = username,
            photo = photo,
            cardNumber = cardNumber,
            joinedText = joinedText,
            onPhotoClick = onPhotoClick,
            modifier = Modifier.graphicsLayer {
                rotationX = deviceTilt.rotationX
                rotationY = deviceTilt.rotationY
                cameraDistance = 14f * density
            },
        )
    }
}

@Composable
internal fun BlurWarningPage(
    disableBlur: Boolean,
    onDisableBlurChange: (Boolean) -> Unit,
    detected: String?,
    onContinue: () -> Unit,
) {
    val glass = remember { GlassState() }
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val side = rememberSidePadding(maxWidth)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glass),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = Onb.MaxContentWidth)
                    .fillMaxSize()
                    .padding(top = Onb.TopClearance + 18.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = side)) {
                    PageBadge(painterResource(R.drawable.warning))
                    Spacer(Modifier.height(22.dp))
                    Text(stringResource(R.string.onb_warning), color = Onb.Title, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = OnbTitleTracking)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.onb_blur_warning_body),
                        color = Onb.Subtitle,
                        fontSize = 18.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = OnbSubtitleTracking,
                    )
                    Spacer(Modifier.height(22.dp))
                    OnbToggleCard(
                        title = stringResource(R.string.onb_disable_blur),
                        description = detected?.let { stringResource(R.string.onb_measured, it) } ?: "",
                        checked = disableBlur,
                        onCheckedChange = onDisableBlurChange,
                        leading = { Box(Modifier.size(22.dp).clip(CircleShape).background(Onb.Badge)) },
                    )
                    Spacer(Modifier.height(18.dp))
                }
                Image(
                    painter = painterResource(R.drawable.onboarding_phone_internals),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = side + 6.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(132.dp + navBar),
        ) {
            ProgressiveGlassBackground(
                state = glass,
                maxBlurRadius = { 30f },
                direction = BlurDirection.TopToBottom,
                steps = 3,
                modifier = Modifier.matchParentSize(),
            )
            val progressiveBar = com.mochyy.mochymusic.ui.component.rememberProgressiveBlur()
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (progressiveBar) {
                            Brush.verticalGradient(0f to Color.Transparent, 0.5f to Onb.Page.copy(alpha = 0.7f), 1f to Onb.Page)
                        } else {
                            androidx.compose.ui.graphics.SolidColor(Onb.Page.copy(alpha = 0.6f))
                        },
                    ),
            )
            OnbButton(
                text = if (disableBlur) stringResource(R.string.onb_continue) else stringResource(R.string.onb_no_continue),
                onClick = onContinue,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = Onb.MaxContentWidth)
                    .navigationBarsPadding()
                    .padding(horizontal = side, vertical = 18.dp),
            )
        }
    }
}

// ---- 10. last one ----

@Composable
internal fun LastOnePage(
    autoCrossfade: Boolean,
    onAutoCrossfadeChange: (Boolean) -> Unit,
    aiArtistFilter: Boolean,
    onAiArtistFilterChange: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    OnboardingPage(
        badge = { PageBadge(painterResource(R.drawable.tune)) },
        title = stringResource(R.string.onb_last_one),
        subtitle = stringResource(R.string.onb_last_one_subtitle),
        footer = { OnbButton(stringResource(R.string.onb_next_lower), onClick = onNext) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnbToggleCard(
                title = stringResource(R.string.onb_auto_crossfade_title),
                description = stringResource(R.string.onb_auto_crossfade_desc),
                checked = autoCrossfade,
                onCheckedChange = onAutoCrossfadeChange,
                header = { CrossfadeBanner() },
            )
            OnbToggleCard(
                title = stringResource(R.string.onb_ai_filter_title),
                description = stringResource(R.string.onb_ai_filter_desc),
                checked = aiArtistFilter,
                onCheckedChange = onAiArtistFilterChange,
            )
        }
    }
}
