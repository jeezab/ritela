package app.ritela.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritela.R
import app.ritela.RitelaApplication
import app.ritela.data.sync.SyncSettingsRepository
import app.ritela.data.sync.SyncSettingsSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
internal fun SyncSettings() {
    val manager = (LocalContext.current.applicationContext as RitelaApplication).profiles
    val active by manager.session.collectAsStateWithLifecycle()
    val session = LocalProfileSession.current ?: active
    var showing by rememberSaveable(session.token) { mutableStateOf(false) }
    SettingsGroup {
        SettingsRow(
            stringResource(R.string.sync_title),
            R.drawable.ic_shield,
            modifier = Modifier.testTag("sync-settings"),
            value = stringResource(R.string.sync_settings_summary)
        ) {
            showing = true
        }
    }
    if (showing) SyncSettingsDialog(session.syncSettings, onClose = { showing = false })
}

private sealed interface SyncSettingsLoad {
    data object Loading : SyncSettingsLoad
    data object Failed : SyncSettingsLoad
    data class Ready(val value: SyncSettingsSnapshot) : SyncSettingsLoad
}

@Composable
internal fun SyncSettingsDialog(repository: SyncSettingsRepository, onClose: () -> Unit) {
    val flow = remember(repository) {
        repository.snapshots.map<SyncSettingsSnapshot, SyncSettingsLoad> {
            SyncSettingsLoad.Ready(it)
        }
            .catch { emit(SyncSettingsLoad.Failed) }
    }
    val state by flow.collectAsStateWithLifecycle(initialValue = SyncSettingsLoad.Loading)
    var working by remember(repository) { mutableStateOf(false) }
    var error by remember(repository) { mutableStateOf(false) }
    var activation by remember(repository) { mutableStateOf(false) }
    val registered = (state as? SyncSettingsLoad.Ready)?.value?.registered == true
    val active = registered && (state as? SyncSettingsLoad.Ready)?.value?.internetRequested == true
    var disable by remember(repository) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun perform(action: suspend () -> Unit) {
        scope.launch {
            working = true
            error = false
            try {
                action()
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (_: Exception) {
                error = true
            } finally {
                working = false
            }
        }
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler(onBack = onClose)
        HomeTheme {
            Column(
                Modifier.fillMaxSize().background(HomeColors.background).systemBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(HomeSpacing.gutter)
                    .testTag("sync-settings-dialog"),
                verticalArrangement = Arrangement.spacedBy(HomeSpacing.gap)
            ) {
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("sync-close")
                ) {
                    Text(stringResource(R.string.done))
                }
                Text(
                    stringResource(R.string.sync_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                SettingsGroup {
                    Text(
                        stringResource(
                            if (active) R.string.sync_internet_enabled else R.string.sync_manual
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(stringResource(R.string.sync_manual_description))
                    OutlinedButton(
                        onClick = { activation = true },
                        enabled = repository.available && !active && !working,
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp).testTag("sync-internet-unavailable")
                    ) {
                        Text(
                            stringResource(
                                if (!repository.available) {
                                    R.string.sync_internet_unavailable
                                } else if (active) {
                                    R.string.sync_internet_enabled
                                } else {
                                    R.string.sync_internet_enable
                                }
                            )
                        )
                    }
                }
                when (val loaded = state) {
                    SyncSettingsLoad.Loading -> Text(stringResource(R.string.loading))

                    SyncSettingsLoad.Failed -> Text(stringResource(R.string.storage_error))

                    is SyncSettingsLoad.Ready -> {
                        if (loaded.value.internetRequested) {
                            SettingsGroup {
                                Text(
                                    stringResource(
                                        if (!repository.available) {
                                            R.string.sync_permission_saved
                                        } else {
                                            R.string.sync_link_help
                                        }
                                    )
                                )
                                if (loaded.value.relayProblem) {
                                    Text(
                                        stringResource(R.string.sync_connection_problem)
                                    )
                                }
                                OutlinedButton(
                                    onClick = { disable = true },
                                    enabled = !working,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .testTag("sync-disable")
                                ) { Text(stringResource(R.string.sync_disable)) }
                            }
                        }
                    }
                }
                if (error) {
                    Text(
                        stringResource(R.string.storage_error),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                SettingsGroup {
                    Text(
                        stringResource(R.string.sync_privacy_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(stringResource(R.string.sync_privacy_future))
                    Text(stringResource(R.string.sync_privacy_relay))
                    Text(stringResource(R.string.sync_privacy_local))
                }
            }
            if (activation) {
                val dismissActivation = {
                    activation = false
                }
                PartnerModal(
                    stringResource(R.string.sync_internet_enable),
                    dismissActivation,
                    footer = {
                        Column {
                            TextButton(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag("sync-activate-confirm"),
                                onClick = {
                                    activation = false
                                    perform { repository.activate() }
                                }
                            ) { Text(stringResource(R.string.sync_consent_confirm)) }
                            TextButton(
                                onClick = dismissActivation,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) { Text(stringResource(R.string.cancel)) }
                        }
                    }
                ) {
                    Text(stringResource(R.string.sync_consent))
                }
            }
            if (disable) {
                AlertDialog(
                    onDismissRequest = { disable = false },
                    title = { Text(stringResource(R.string.sync_disable)) },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(stringResource(R.string.sync_disable_confirmation))
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !working,
                            modifier = Modifier.testTag(
                                "sync-confirm-action"
                            ),
                            onClick = {
                                disable = false
                                perform { repository.disable() }
                            }
                        ) { Text(stringResource(R.string.sync_confirm)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { disable = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}
