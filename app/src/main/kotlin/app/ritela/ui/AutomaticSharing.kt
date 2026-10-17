package app.ritela.ui

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritela.R
import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun AutomaticApproval(message: PartnerMessage) {
    val session = LocalProfileSession.current ?: return
    val request = remember(message.body) { JSONObject(message.body).getJSONObject("authorRequest") }
    val selection = remember(message.body) { PartnerCodec.scope(request.getJSONObject("scope")) }
    val formatter = PartnerFormatter(LocalResources.current)
    val scope = rememberCoroutineScope()
    val approved by remember(session, message.id) {
        session.automaticSync.approval(message.id)
    }.collectAsStateWithLifecycle(initialValue = false)
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    Text(stringResource(R.string.sync_auto_request), style = MaterialTheme.typography.titleMedium)
    Text(formatter.access(selection))
    if (selection.tags.isNotEmpty() || selection.sections.isNotEmpty()) {
        Text(
            stringResource(
                R.string.sync_auto_fields,
                selection.tags.values.sumOf { it.size } + selection.sections.size
            )
        )
    }
    if (message.outgoing) {
        Text(
            stringResource(
                if (approved) R.string.sync_auto_approved else R.string.sync_auto_waiting
            )
        )
        return
    }
    Text(
        stringResource(R.string.sync_auto_receive_consent),
        style = MaterialTheme.typography.bodySmall
    )
    Button(
        onClick = {
            working = true
            error = false
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        session.automaticSync.approve(message.id)
                        session.syncNow(retry = true)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error = true
                } finally {
                    working = false
                }
            }
        },
        enabled = !working && !approved,
        modifier = Modifier.testTag(
            "sync-auto-approve-${message.id}"
        )
    ) {
        Text(
            stringResource(
                if (approved) R.string.sync_auto_approved else R.string.sync_auto_approve
            )
        )
    }
    if (error) {
        Text(
            stringResource(R.string.sync_problem_validation),
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
internal fun AutomaticSharingControls(peer: String, device: String) {
    val session = LocalProfileSession.current ?: return
    val links by session.automaticSync.links.collectAsStateWithLifecycle(initialValue = emptyList())
    val enabled = links.any {
        it.peerIdentity == peer && it.peerDevice == device &&
            it.automaticEnabled == 1
    }
    if (enabled) {
        Text(stringResource(R.string.sync_auto_active))
        OutlinedButton(
            onClick = {
                session.partnerModel.run { session.automaticSync.pause(peer, device) }
            },
            modifier = Modifier.testTag("sync-auto-pause-$device")
        ) {
            Text(stringResource(R.string.sync_auto_pause))
        }
    }
}
