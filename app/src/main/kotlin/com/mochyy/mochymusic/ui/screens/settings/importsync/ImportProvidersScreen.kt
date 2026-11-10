package com.mochyy.mochymusic.ui.screens.settings.importsync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.importer.LinkSource
import com.mochyy.mochymusic.importer.account.AccountService
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.viewmodels.ImportSyncViewModel

@Composable
fun ImportProvidersScreen(
    navController: NavController,
    viewModel: ImportSyncViewModel = hiltViewModel(),
) {
    var linkSource by rememberSaveable { mutableStateOf<LinkSource?>(null) }

    // some file managers label .csv as text/plain or octet-stream, so don't filter too hard
    val backupPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.loadFile(uri, sourceLabel = "Mochy backup")
        }

    SubSettingsScaffold(
        title = stringResource(R.string.import_sync_import_title),
        onBack = { navController.navigateUp() },
    ) {
        SettingsGroup(
            title = stringResource(R.string.import_providers_signin_header),
            style = SettingsGroupStyle.Grouped,
            items =
                listOf(
                    AccountService.SPOTIFY to stringResource(R.string.import_provider_spotify_desc),
                    AccountService.APPLE_MUSIC to stringResource(R.string.import_provider_apple_music_desc),
                    AccountService.YOUTUBE_MUSIC to stringResource(R.string.import_provider_ytm_desc),
                    AccountService.TIDAL to stringResource(R.string.import_provider_tidal_desc),
                ).map { (service, description) ->
                    SettingsItem(
                        title = { Text(service.label) },
                        highlightKey = "${service.label} account",
                        descriptionText = description,
                        icon = painterResource(R.drawable.login),
                        iconShape = CircleShape,
                        onClick = { navController.navigate(accountImportRoute(service)) },
                    )
                },
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = stringResource(R.string.import_providers_link_header),
            style = SettingsGroupStyle.Grouped,
            items =
                LinkSource.entries.map { source ->
                    SettingsItem(
                        title = { Text(source.label) },
                        highlightKey = source.label,
                        descriptionText =
                            when (source) {
                                LinkSource.SPOTIFY -> stringResource(R.string.import_link_spotify_desc)
                                LinkSource.APPLE_MUSIC -> stringResource(R.string.import_link_apple_music_desc)
                                LinkSource.DEEZER -> stringResource(R.string.import_link_deezer_desc)
                                LinkSource.YOUTUBE -> stringResource(R.string.import_link_youtube_desc)
                            },
                        icon = painterResource(R.drawable.link),
                        iconShape = CircleShape,
                        onClick = { linkSource = source },
                    )
                },
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = stringResource(R.string.import_providers_file_header),
            style = SettingsGroupStyle.Grouped,
            items =
                listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.import_mochy_backup_title)) },
                        highlightKey = "Mochy backup",
                        descriptionText = stringResource(R.string.import_mochy_backup_desc),
                        icon = painterResource(R.drawable.backup),
                        iconShape = CircleShape,
                        onClick = {
                            backupPicker.launch(
                                arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"),
                            )
                        },
                    ),
                    SettingsItem(
                        title = { Text(stringResource(R.string.import_sync_tunemymusic_title)) },
                        highlightKey = "TuneMyMusic CSV",
                        descriptionText = stringResource(R.string.import_tunemymusic_csv_desc),
                        icon = painterResource(R.drawable.web_link),
                        iconShape = CircleShape,
                        onClick = { navController.navigate(ImportTuneMyMusicRoute) },
                    ),
                ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = stringResource(R.string.import_providers_other_header),
            style = SettingsGroupStyle.Grouped,
            items =
                listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.import_other_services_title)) },
                        descriptionText = stringResource(R.string.import_other_services_desc),
                        detail = stringResource(R.string.import_other_services_detail),
                        icon = painterResource(R.drawable.library_music),
                        iconShape = CircleShape,
                        onClick = { navController.navigate(ImportTuneMyMusicRoute) },
                    ),
                ),
        )
    }

    linkSource?.let { source ->
        LinkInputDialog(
            source = source,
            onDismiss = { linkSource = null },
            onSubmit = { link ->
                linkSource = null
                viewModel.loadLink(link)
            },
        )
    }

    PendingImportHost(viewModel = viewModel, navController = navController)
}
