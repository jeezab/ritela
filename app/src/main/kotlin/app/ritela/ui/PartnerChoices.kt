package app.ritela.ui

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritela.R
import app.ritela.domain.JournalLayout
import app.ritela.domain.PartnerContact
import app.ritela.domain.PartnerIdentity
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PartnerManagement(
    model: PartnerViewModel,
    contact: PartnerContact,
    identity: PartnerIdentity,
    dismiss: () -> Unit,
    initialAction: String? = null
) {
    val directory by model.directory.collectAsStateWithLifecycle()
    val messages by model.messages.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val source = directory.identities.firstOrNull { it.id == identity.id } ?: identity
    var confirmAction by remember { mutableStateOf(initialAction?.takeIf { it == "DELETE" }) }
    var other by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf(contact.name) }
    var group by remember { mutableStateOf(false) }
    val combineFocus = remember { BringIntoViewRequester() }
    LaunchedEffect(initialAction) {
        if (initialAction in setOf("COMBINE", "GROUP")) {
            androidx.compose.runtime.withFrameNanos { }
            combineFocus.bringIntoView()
        }
    }
    val formatter = PartnerFormatter(LocalResources.current)
    PartnerModal(stringResource(R.string.partner_manage), dismiss) {
        Text(contact.name, style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            name,
            { if (it.length <= 80) name = it },
            label = { Text(stringResource(R.string.partner_profile_name)) }
        )
        TextButton(
            { model.run { model.repository.rename(contact.id, name) } },
            enabled = !busy && name.isNotBlank() && name.trim() != contact.name
        ) {
            Text(stringResource(R.string.save))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.partner_allow_exchange), Modifier.weight(1f))
            Switch(source.exchangeEnabled, { value ->
                model.run { model.repository.enable(source.id, value) }
            }, enabled = !busy)
        }
        Text(
            stringResource(R.string.partner_contact_devices),
            style = MaterialTheme.typography.titleMedium
        )
        Text(formatter.access(source.grants))
        source.devices.forEachIndexed { index, device ->
            Text(stringResource(R.string.partner_device_number, index + 1))
            device.verificationContext?.let { context ->
                Text(
                    stringResource(R.string.partner_code_label) + ": " +
                        app.ritela.data.pairingCode(context)
                )
            }
            Text(
                stringResource(
                    if (device.mutualConfirmed) {
                        R.string.partner_linked
                    } else {
                        R.string.partner_pending_connection
                    }
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.partner_device_enabled), Modifier.weight(1f))
                Switch(device.enabled, { value ->
                    model.run { model.repository.enableDevice(source.id, device.id, value) }
                }, enabled = !busy && device.mutualConfirmed)
            }
        }
        Text(
            stringResource(R.string.partner_new_device_hint),
            style = MaterialTheme.typography.bodySmall
        )
        FlowRow {
            TextButton({
                confirmAction = "REVOKE"
            }, enabled = !busy) { Text(stringResource(R.string.partner_revoke)) }
            TextButton({
                confirmAction = "CLEAR"
            }, enabled = !busy) { Text(stringResource(R.string.partner_clear)) }
            TextButton({
                confirmAction = "DELETE"
            }, enabled = !busy) { Text(stringResource(R.string.partner_delete)) }
        }
        if (contact.identities.size >
            1
        ) {
            TextButton({
                confirmAction = "SPLIT"
            }, enabled = !busy) { Text(stringResource(R.string.partner_split)) }
        }
        Text(
            stringResource(R.string.partner_combine),
            Modifier.bringIntoViewRequester(combineFocus),
            style = MaterialTheme.typography.titleMedium
        )
        directory.contacts.filter { it.id != contact.id && !it.group }.forEach { item ->
            TextButton({ other = item.id }) {
                Text(
                    (
                        if (other ==
                            item.id
                        ) {
                            "• "
                        } else {
                            ""
                        }
                        ) + item.name
                )
            }
        }
        other?.let { id ->
            val second = directory.contacts.first { it.id == id }
            listOf(contact, second).forEach { item ->
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                directory.identities.filter { it.id in item.identities }.forEach { person ->
                    Text(formatter.access(person.grants))
                    Text(
                        formatter.comparison(
                            person.devices.size,
                            messages.count {
                                it.identity ==
                                    person.id
                            }
                        )
                    )
                }
            }
            Text(stringResource(R.string.partner_combine_hint))
            OutlinedTextField(name, {
                if (it.length <=
                    80
                ) {
                    name = it
                }
            }, label = { Text(stringResource(R.string.partner_profile_name)) })
            FlowRow {
                TextButton(
                    {
                        group = false
                        confirmAction = "MERGE"
                    },
                    enabled =
                        name.isNotBlank() && !busy
                ) { Text(stringResource(R.string.partner_merge)) }
                TextButton(
                    {
                        group = true
                        confirmAction = "MERGE"
                    },
                    enabled =
                        name.isNotBlank() && !busy
                ) {
                    Text(stringResource(R.string.partner_create_group))
                }
            }
        }
    }
    confirmAction?.let { action ->
        PartnerModal(stringResource(R.string.partner_confirm), {
            confirmAction =
                null
        }) {
            Text(
                stringResource(
                    when (action) {
                        "DELETE" -> R.string.partner_delete_hint
                        "CLEAR" -> R.string.partner_clear_hint
                        "REVOKE" -> R.string.partner_revoke_hint
                        else -> R.string.partner_combine_hint
                    }
                )
            )
            Button({
                model.run {
                    when (action) {
                        "DELETE" -> model.repository.remove(contact.id)

                        "CLEAR" -> model.repository.clearReceived(source.id)

                        "REVOKE" -> model.repository.grant(source.id, ShareScope())

                        "SPLIT" -> model.repository.split(contact.id)

                        "MERGE" -> model.repository.merge(
                            contact.id,
                            requireNotNull(other),
                            name,
                            group
                        )
                    }
                }
                confirmAction = null
                if (action in listOf("DELETE", "MERGE", "SPLIT")) dismiss()
            }, enabled = !busy) { Text(stringResource(R.string.partner_continue)) }
        }
    }
}
