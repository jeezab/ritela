package app.ritela.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
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
        SettingsGroup {
            SettingsRow(
                stringResource(R.string.language_title),
                R.drawable.ic_language,
                value = LocalConfiguration.current.locales[0].getDisplayLanguage(
                    LocalConfiguration.current.locales[0]
                )
            ) {
                val intent = if (Build.VERSION.SDK_INT >= 33) {
                    Intent(
                        Settings.ACTION_APP_LOCALE_SETTINGS,
                        "package:${context.packageName}".toUri()
                    )
                } else {
                    Intent(Settings.ACTION_LOCALE_SETTINGS)
                }
                context.startActivity(intent)
            }
        }
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
