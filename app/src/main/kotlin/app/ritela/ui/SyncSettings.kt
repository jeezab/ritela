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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritela.R
import app.ritela.RitelaApplication
import app.ritela.data.sync.SyncQueueContents
import app.ritela.data.sync.SyncQueueEntry
import app.ritela.data.sync.SyncQueueStatus
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
    val session = LocalProfileSession.current
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
    var token by remember(repository) { mutableStateOf("") }
    val registered = (state as? SyncSettingsLoad.Ready)?.value?.registered == true
    val active = registered && (state as? SyncSettingsLoad.Ready)?.value?.internetRequested == true
    var disable by remember(repository) { mutableStateOf(false) }
    var cancelling by remember(repository) { mutableStateOf<SyncQueueEntry?>(null) }
    var details by remember(repository) { mutableStateOf<SyncQueueContents?>(null) }
    var showDetails by remember(repository) { mutableStateOf(false) }
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
                                SyncAction(R.string.sync_disable, "sync-disable", !working) {
                                    disable =
                                        true
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.sync_queue_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.sync_queue_description),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (loaded.value.entries.isEmpty()) {
                            Text(
                                stringResource(R.string.sync_queue_empty),
                                modifier = Modifier.testTag("sync-queue-empty")
                            )
                        }
                        loaded.value.entries.forEach { entry ->
                            SyncQueueCard(
                                entry,
                                working,
                                onDetails = {
                                    perform {
                                        details = repository.contents(entry.key)
                                        showDetails =
                                            true
                                    }
                                },
                                onCancel = { cancelling = entry },
                                onRetry = { perform { session?.syncNow(retry = true) } }
                            )
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
                    token = ""
                }
                PartnerModal(
                    stringResource(R.string.sync_internet_enable),
                    dismissActivation,
                    footer = {
                        Column {
                            TextButton(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag("sync-activate-confirm"),
                                enabled = registered || token.matches(Regex("[0-9a-f]{64}")),
                                onClick = {
                                    val value = token
                                    token = ""
                                    activation = false
                                    perform { repository.activate(value) }
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
                    if (!registered) {
                        androidx.compose.material3.OutlinedTextField(
                            token,
                            { if (it.length <= 64) token = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.sync_activation)) },
                            modifier = Modifier.fillMaxWidth().testTag("sync-activation-token"),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false
                            )
                        )
                    }
                }
            }
            if (disable || cancelling != null) {
                val entry = cancelling
                AlertDialog(
                    onDismissRequest = {
                        disable = false
                        cancelling = null
                    },
                    title = {
                        Text(
                            stringResource(
                                if (disable) {
                                    R.string.sync_disable
                                } else if (entry?.records ==
                                    true
                                ) {
                                    R.string.sync_pause_records
                                } else {
                                    R.string.sync_cancel_send
                                }
                            )
                        )
                    },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                stringResource(
                                    if (disable) {
                                        R.string.sync_disable_confirmation
                                    } else if (entry?.records ==
                                        true
                                    ) {
                                        R.string.sync_pause_confirmation
                                    } else {
                                        R.string.sync_cancel_confirmation
                                    }
                                )
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !working,
                            modifier = Modifier.testTag(
                                "sync-confirm-action"
                            ),
                            onClick = {
                                val disabling = disable
                                disable = false
                                cancelling = null
                                perform {
                                    if (disabling) {
                                        repository.disable()
                                    } else {
                                        entry?.let {
                                            repository.cancel(it.key)
                                        }
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.sync_confirm)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            disable = false
                            cancelling = null
                        }) { Text(stringResource(R.string.cancel)) }
                    }
                )
            }
            if (showDetails) {
                AlertDialog(
                    onDismissRequest = { showDetails = false },
                    title = { Text(stringResource(R.string.sync_contents)) },
                    text = {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val contents = details
                            if (contents ==
                                null
                            ) {
                                Text(stringResource(R.string.sync_contents_unavailable))
                            } else {
                                contents.categories.forEach {
                                    Text(stringResource(PartnerFormatter.category(it)))
                                }
                                if (contents.note) Text(stringResource(R.string.sync_note))
                                if (contents.doodle) {
                                    Text(
                                        stringResource(R.string.partner_doodle_kind)
                                    )
                                }
                                if (contents.categories.isEmpty() && !contents.note &&
                                    !contents.doodle
                                ) {
                                    Text(stringResource(R.string.sync_receipt))
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            modifier = Modifier.testTag("sync-details-close"),
                            onClick = {
                                showDetails = false
                            }
                        ) { Text(stringResource(R.string.done)) }
                    }
                )
            }
        }
    }
}

@Composable
private fun SyncQueueCard(
    entry: SyncQueueEntry,
    working: Boolean,
    onDetails: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    SettingsGroup(Modifier.testTag("sync-entry-${entry.key}")) {
        Text(
            entry.recipient ?: stringResource(R.string.sync_recipient_unavailable),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(
                when {
                    entry.records -> R.string.sync_records
                    entry.kind == "DOODLE" -> R.string.partner_doodle_kind
                    entry.kind == "DATA" -> R.string.sync_message
                    entry.kind == "SYNC_CAPS" -> R.string.sync_connection_controls
                    else -> R.string.sync_receipt
                }
            )
        )
        Text(
            stringResource(
                when (entry.status) {
                    SyncQueueStatus.WAITING -> R.string.sync_waiting

                    SyncQueueStatus.BLOCKED -> if (entry.tooLarge) {
                        R.string.sync_too_large
                    } else {
                        R.string.sync_blocked
                    }

                    SyncQueueStatus.AWAITING_RECEIPT -> R.string.sync_awaiting_receipt

                    SyncQueueStatus.DELIVERED -> R.string.sync_delivered

                    SyncQueueStatus.CANCELLED -> R.string.sync_cancelled

                    SyncQueueStatus.UNAVAILABLE -> R.string.sync_status_unavailable
                }
            ),
            modifier = Modifier.testTag("sync-status-${entry.key}")
        )
        SyncAction(R.string.sync_contents, "sync-contents-${entry.key}", !working, onDetails)
        entry.problem?.let { problem ->
            Text(stringResource(syncProblemText(problem)), color = MaterialTheme.colorScheme.error)
            if (!entry.tooLarge) {
                SyncAction(R.string.sync_chat_retry, "sync-retry-${entry.key}", !working, onRetry)
            }
        }
        if (entry.canCancel) {
            SyncAction(
                if (entry.records) R.string.sync_pause_records else R.string.sync_cancel_send,
                "sync-cancel-${entry.key}",
                !working,
                onCancel
            )
        }
    }
}

@Composable
private fun SyncAction(label: Int, tag: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
    ) {
        Text(stringResource(label))
    }
}
