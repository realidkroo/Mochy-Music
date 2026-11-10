// the developer playground page: what the repo did since this build, its issues, and the experimental settings

package com.mochyy.mochymusic.ui.screens.settings.devspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.settings.CategoryBubble

const val DevPlaygroundRoute = "developer_playground"

@Composable
fun DevPlaygroundScreen(navController: NavController) {
    val stats by DevSpaceRepository.current.collectAsState()
    val loading by DevSpaceRepository.loading.collectAsState()
    val error by DevSpaceRepository.error.collectAsState()
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { DevSpaceRepository.refresh(BuildConfig.BUILD_TIME_MILLIS, force = reload > 0) }
    val settled = stats != null || !loading

    SubSettingsScaffold(
        title = stringResource(R.string.dev_playground_title),
        backLabel = stringResource(R.string.dev_spaces_back_label),
        onBack = { navController.navigateUp() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RepoFiguresCard(
                title = stringResource(R.string.dev_since_this_build),
                subtitle = stringResource(R.string.dev_since_build_subtitle),
                figures =
                    listOf(
                        stats?.newIssues to stringResource(R.string.dev_new_reported_issues),
                        stats?.newResolved to stringResource(R.string.dev_new_resolved_issue),
                        stats?.commits to stringResource(R.string.dev_commits),
                        stats?.releases to stringResource(R.string.dev_releases),
                    ),
                settled = settled,
            )
            RepoFiguresCard(
                title = stringResource(R.string.dev_in_total),
                figures =
                    listOf(
                        stats?.reportedTotal to stringResource(R.string.dev_reported_issues),
                        stats?.closedTotal to stringResource(R.string.dev_resolved_issue),
                    ),
                settled = settled,
            )
            RepoIssuesSection(stats = stats, loading = loading, error = error, onRetry = { reload++ })
            Spacer(Modifier.height(6.dp))
            CategoryBubble(
                title = stringResource(R.string.dev_experimental_settings_bubble),
                iconRes = R.drawable.biotech,
                onClick = { navController.navigate("experimental_settings") },
            )
        }
    }
}
