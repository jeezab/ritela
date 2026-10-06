package app.ritela.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.net.toUri
import app.ritela.R
import app.ritela.data.ThemeMode

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    state: PeriodUiState,
    onThemeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    var choosingTheme by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(padding)
            .verticalScroll(rememberScrollState()).padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.large)
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.testTag("settings-heading")
        )
        Text(
            stringResource(R.string.appearance_title),
            style = MaterialTheme.typography.titleMedium
        )
        HorizontalDivider()
        TextButton(onClick = {
            val intent = if (Build.VERSION.SDK_INT >= 33) {
                Intent(
                    Settings.ACTION_APP_LOCALE_SETTINGS,
                    "package:${context.packageName}".toUri()
                )
            } else {
                Intent(Settings.ACTION_LOCALE_SETTINGS)
            }
            context.startActivity(intent)
        }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.language_title), Modifier.weight(1f))
                Text(
                    LocalConfiguration.current.locales[0].getDisplayLanguage(
                        LocalConfiguration.current.locales[0]
                    ) +
                        "  \u203a"
                )
            }
        }
        TextButton(onClick = {
            choosingTheme = true
        }, modifier = Modifier.fillMaxWidth().testTag("theme-selector")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.theme_title), Modifier.weight(1f))
                Text(
                    stringResource(
                        when (state.themeMode) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                        }
                    ) + "  \u203a"
                )
            }
        }
        HorizontalDivider()
        state.problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
        BackupActions()
    }
    if (choosingTheme) {
        AlertDialog(
            onDismissRequest = { choosingTheme = false },
            title = { Text(stringResource(R.string.theme_title)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { theme ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = Spacing.actionHeight)
                                .testTag("theme-${theme.name.lowercase()}")
                                .selectable(
                                    selected = state.themeMode == theme,
                                    enabled = !state.saving,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onThemeChange(theme)
                                        choosingTheme = false
                                    }
                                )
                                .padding(horizontal = Spacing.small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = state.themeMode == theme, onClick = null)
                            Text(
                                stringResource(
                                    when (theme) {
                                        ThemeMode.SYSTEM -> R.string.theme_system
                                        ThemeMode.LIGHT -> R.string.theme_light
                                        ThemeMode.DARK -> R.string.theme_dark
                                    }
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    choosingTheme = false
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
