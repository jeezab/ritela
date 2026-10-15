package app.ritela.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val version = remember(context) { appVersionLabel(context) }
    Box(Modifier.fillMaxSize().background(HomeColors.background).padding(padding)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(
                rememberScrollState()
            ).padding(HomeSpacing.gutter),
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
            PartnerSettings()
            state.problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
            SettingsGroup { BackupActions() }
            Text(
                stringResource(R.string.privacy_description),
                style = MaterialTheme.typography.bodySmall,
                color = HomeColors.muted
            )
            WithLoveButton(onLoveClick, modifier)
            if (version.isNotEmpty()) {
                Text(
                    version,
                    Modifier.align(Alignment.End).testTag("app-version"),
                    style = MaterialTheme.typography.labelSmall,
                    color = HomeColors.muted.copy(alpha = 0.72f)
                )
            }
        }
    }
}

private fun appVersionLabel(context: android.content.Context): String {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val name = info.versionName.orEmpty()
    return if (name.isEmpty()) "" else "v$name"
}
