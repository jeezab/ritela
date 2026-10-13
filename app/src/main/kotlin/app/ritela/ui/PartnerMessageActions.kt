package app.ritela.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ritela.R
import app.ritela.data.PartnerMessage
import app.ritela.data.hasSharedData

@Composable
internal fun PartnerMessageActions(
    message: PartnerMessage,
    enabled: Boolean,
    delete: ((Boolean) -> Unit)?,
    content: @Composable () -> Unit
) {
    var menu by remember(message.id) { mutableStateOf(false) }
    var erase by remember(message.id) { mutableStateOf(false) }
    var confirming by remember(message.id) { mutableStateOf(false) }
    val shared = remember(message.body, message.outgoing) { message.hasSharedData() }
    val label = stringResource(R.string.delete)
    fun showMenu() {
        erase = false
        menu = true
    }
    Box(
        Modifier.fillMaxWidth(.86f).testTag("partner-message-actions-${message.id}")
            .pointerInput(message.id, enabled, delete != null) {
                if (enabled && delete != null) detectTapGestures(onLongPress = { showMenu() })
            }.semantics {
                if (enabled && delete != null) {
                    onLongClick(label) {
                        showMenu()
                        true
                    }
                }
            }
    ) {
        content()
        DropdownMenu(menu, { menu = false }) {
            if (shared) {
                Row(
                    Modifier.fillMaxWidth().testTag("partner-message-erase")
                        .toggleable(erase, enabled, Role.Checkbox) { erase = it }.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(erase, null, enabled = enabled)
                    Text(stringResource(R.string.partner_message_erase_data))
                }
            }
            DropdownMenuItem({ Text(label) }, {
                menu = false
                confirming = true
            }, modifier = Modifier.testTag("partner-message-delete"), enabled = enabled)
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.partner_message_delete_title)) },
            text = {
                Text(
                    stringResource(
                        if (!shared) {
                            R.string.partner_message_delete_contents
                        } else if (erase) {
                            R.string.partner_message_delete_data
                        } else {
                            R.string.partner_message_hide
                        }
                    )
                )
            },
            confirmButton = {
                Button(
                    {
                        confirming = false
                        delete?.invoke(shared && erase)
                    },
                    Modifier.testTag("partner-message-delete-confirm"),
                    enabled = enabled
                ) {
                    Text(label)
                }
            },
            dismissButton = {
                OutlinedButton({ confirming = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
