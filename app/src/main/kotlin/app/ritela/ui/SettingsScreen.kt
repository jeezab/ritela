package app.ritela.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import app.ritela.R

@Composable
fun SettingsScreen(padding: PaddingValues, state: PeriodUiState) {
    HomeTheme {
        LoveSurprise { onClick, anchor -> SettingsContent(padding, state, onClick, anchor) }
    }
}

@Composable
private fun SettingsContent(
    padding: PaddingValues,
    state: PeriodUiState,
    onLoveClick: () -> Unit,
    modifier: Modifier
) {
    Column(
        Modifier.fillMaxSize().background(HomeColors.background).padding(padding)
            .verticalScroll(rememberScrollState()).padding(HomeSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(HomeSpacing.gap)
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 32.sp)
        )
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("settings-heading")
        )
        UserSettings(state)
        state.problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
        SettingsGroup { BackupActions() }
        Text(
            stringResource(R.string.privacy_description),
            style = MaterialTheme.typography.bodySmall,
            color = HomeColors.muted
        )
        WithLoveButton(onLoveClick, modifier)
    }
}
