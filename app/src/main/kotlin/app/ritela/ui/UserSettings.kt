package app.ritela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritela.R
import app.ritela.RitelaApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UserSettings(state: PeriodUiState) {
    val manager = (LocalContext.current.applicationContext as RitelaApplication).profiles
    val activeSession by manager.session.collectAsStateWithLifecycle()
    val session = LocalProfileSession.current ?: activeSession
    val users by manager.registry.profiles.collectAsStateWithLifecycle()
    val switching by manager.switching.collectAsStateWithLifecycle()
    val backupBusy by session.backupActive.collectAsStateWithLifecycle()
    val language by session.settings.language.collectAsStateWithLifecycle()
    var showingUsers by rememberSaveable { mutableStateOf(false) }
    var showingLanguage by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    var working by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val enabled = !state.saving && !switching && !backupBusy && !working
    SettingsGroup {
        SettingsRow(
            stringResource(R.string.language_title),
            R.drawable.ic_language,
            value = when (language) {
                "ru" -> "Русский"
                "en" -> "English"
                else -> stringResource(R.string.language_system)
            },
            enabled = enabled,
            modifier = Modifier.testTag("profile-language")
        ) { showingLanguage = true }
    }
    SettingsGroup {
        SettingsRow(
            stringResource(R.string.profile_switch),
            R.drawable.ic_user,
            value = users.first { it.id == session.profileId }.name,
            enabled = enabled,
            modifier = Modifier.testTag("profile-switch")
        ) {
            showingUsers = true
            error = false
        }
    }
    if (showingLanguage) {
        AlertDialog(
            onDismissRequest = { if (!working) showingLanguage = false },
            title = { Text(stringResource(R.string.language_title)) },
            text = {
                Column {
                    listOf("system", "ru", "en").forEach { tag ->
                        TextButton({
                            working = true
                            scope.launch {
                                try {
                                    session.settings.saveLanguage(tag)
                                    showingLanguage = false
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    error = true
                                } finally {
                                    working = false
                                }
                            }
                        }, enabled = !working, modifier = Modifier.testTag("language-$tag")) {
                            RadioButton(tag == language, onClick = null)
                            Text(
                                when (tag) {
                                    "ru" -> "Русский"
                                    "en" -> "English"
                                    else -> stringResource(R.string.language_system)
                                }
                            )
                        }
                    }
                    if (error) Text(stringResource(R.string.storage_error))
                }
            },
            confirmButton = {
                TextButton({ showingLanguage = false }, enabled = !working) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    if (showingUsers && editing == null) {
        AlertDialog(
            onDismissRequest = { if (enabled) showingUsers = false },
            modifier = Modifier.testTag("profile-picker"),
            title = { Text(stringResource(R.string.profile_switch)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    users.forEach { profile ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                {
                                    scope.launch {
                                        try {
                                            manager.switchTo(profile.id)
                                            showingUsers = false
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            error = true
                                        }
                                    }
                                },
                                enabled = enabled,
                                modifier = Modifier.weight(1f)
                                    .testTag("profile-select-${profile.id}")
                            ) {
                                RadioButton(profile.id == session.profileId, onClick = null)
                                Text(profile.name, Modifier.weight(1f).padding(start = 4.dp))
                            }
                            TextButton(
                                {
                                    editing = profile.id
                                    name = profile.name
                                    error = false
                                },
                                enabled = enabled,
                                modifier = Modifier.testTag("profile-rename-${profile.id}")
                            ) {
                                Text(stringResource(R.string.edit))
                            }
                        }
                    }
                    if (error) {
                        Text(
                            stringResource(R.string.storage_error),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    {
                        editing = "new"
                        name = ""
                        error = false
                    },
                    enabled = enabled,
                    modifier = Modifier.testTag("profile-add")
                ) {
                    Text(stringResource(R.string.profile_add))
                }
            },
            dismissButton = {
                TextButton({ showingUsers = false }, enabled = enabled) {
                    Text(stringResource(R.string.done))
                }
            }
        )
    }
    if (editing != null) {
        JournalDialog(
            onDismissRequest = { if (!working) editing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                Modifier.fillMaxWidth().padding(Spacing.large).imePadding(),
                shape = MaterialTheme.shapes.extraLarge,
                color = HomeColors.bottom
            ) {
                Column(
                    Modifier.padding(Spacing.large).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    Text(
                        stringResource(
                            if (editing == "new") R.string.profile_add else R.string.profile_rename
                        ),
                        style = MaterialTheme.typography.titleLarge
                    )
                    OutlinedTextField(
                        name,
                        {
                            name = it
                            error = false
                        },
                        singleLine = true,
                        label = { Text(stringResource(R.string.profile_name)) },
                        modifier = Modifier.fillMaxWidth().testTag("profile-name"),
                        enabled = !working
                    )
                    if (error) {
                        Text(
                            stringResource(R.string.profile_error),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        TextButton({ editing = null }, enabled = !working) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(
                            {
                                val id = editing
                                val proposedName = name
                                working = true
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            if (id == "new") {
                                                manager.registry.add(proposedName)
                                            } else {
                                                manager.registry.rename(
                                                    requireNotNull(id),
                                                    proposedName
                                                )
                                            }
                                        }
                                        editing = null
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        error = true
                                    } finally {
                                        working = false
                                    }
                                }
                            },
                            enabled = !working && name.isNotBlank(),
                            modifier = Modifier.testTag("profile-name-save")
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }
}
