package app.ritela.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import app.ritela.R
import app.ritela.data.ThemeMode

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    state: PeriodUiState,
    onThemeChange: (ThemeMode) -> Unit,
    onDurationChange: (Int) -> Unit = {}
) {
    HomeTheme { SettingsContent(padding, state, onThemeChange, onDurationChange) }
}

@Composable
private fun SettingsContent(
    padding: PaddingValues,
    state: PeriodUiState,
    onThemeChange: (ThemeMode) -> Unit,
    onDurationChange: (Int) -> Unit
) {
    val context = LocalContext.current
    var choosingTheme by rememberSaveable { mutableStateOf(false) }
    var choosingDuration by rememberSaveable { mutableStateOf(false) }
    var duration by rememberSaveable(state.defaults.periodDuration) {
        mutableStateOf(state.defaults.periodDuration.toString())
    }
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
            Text(
                stringResource(R.string.appearance_title),
                style = MaterialTheme.typography.labelLarge,
                color = HomeColors.muted
            )
            SettingsRow(
                stringResource(R.string.language_title),
                R.drawable.ic_note,
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
            SettingsRow(
                stringResource(R.string.theme_title),
                R.drawable.ic_star,
                modifier = Modifier.testTag("theme-selector"),
                value = stringResource(
                    when (state.themeMode) {
                        ThemeMode.SYSTEM -> R.string.theme_system
                        ThemeMode.LIGHT -> R.string.theme_light
                        ThemeMode.DARK -> R.string.theme_dark
                    }
                ),
                enabled = !state.saving
            ) { choosingTheme = true }
        }
        state.problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
        SettingsGroup {
            SettingsRow(
                pluralStringResource(
                    R.plurals.forecast_duration_value,
                    state.defaults.periodDuration,
                    state.defaults.periodDuration
                ),
                R.drawable.ic_calendar,
                modifier = Modifier.testTag("forecast-duration"),
                enabled = !state.saving
            ) { choosingDuration = true }
        }
        SettingsGroup { BackupActions() }
        Text(
            stringResource(R.string.privacy_description),
            style = MaterialTheme.typography.bodySmall,
            color = HomeColors.muted
        )
    }
    if (choosingDuration) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { if (!state.saving) choosingDuration = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                Column(
                    Modifier.padding(Spacing.large).imePadding(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    Text(
                        stringResource(R.string.forecast_duration),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                    ) {
                        Text(stringResource(R.string.forecast_duration_hint))
                        OutlinedTextField(
                            value = duration,
                            onValueChange = { duration = it },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            label = { Text(stringResource(R.string.days_label)) },
                            modifier = Modifier.fillMaxWidth().testTag("forecast-duration-input")
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = {
                            choosingDuration = false
                        }, enabled = !state.saving) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(
                            enabled = duration.toIntOrNull() in 1..60 && !state.saving,
                            onClick = {
                                onDurationChange(duration.toInt())
                                choosingDuration = false
                            }
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
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
