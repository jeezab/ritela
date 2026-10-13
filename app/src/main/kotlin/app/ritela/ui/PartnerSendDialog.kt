package app.ritela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ritela.R
import app.ritela.data.PartnerMessage

/** A view-owned request: retained packets never open a window by themselves. */
internal class PartnerSendRequests {
    private var context: List<String?>? = null
    private var revision = 0L
    var message by mutableStateOf<PartnerMessage?>(null)
        private set

    fun bind(profile: String, chat: String?, identity: String?) {
        val next = listOf(profile, chat, identity)
        if (context != next) {
            context = next
            invalidate()
        }
    }
    fun request(): Long = revision
    fun present(request: Long, prepared: PartnerMessage) {
        if (request == revision) message = prepared
    }
    fun dismiss() {
        message = null
    }
    fun invalidate() {
        revision++
        dismiss()
    }
}

@Composable
internal fun EqualHeightActions(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
internal fun PartnerSendDialog(
    message: PartnerMessage,
    busy: Boolean,
    dismiss: () -> Unit,
    file: () -> Unit,
    bluetooth: () -> Unit
) {
    PartnerModal(stringResource(R.string.partner_transport), dismiss, closeButton = true) {
        Text(
            stringResource(R.string.partner_waiting_to_send) + "\n" +
                PartnerFormatter(LocalResources.current).moment(message.received)
        )
        EqualHeightActions {
            Button(
                file,
                Modifier.weight(1f).fillMaxHeight().testTag("partner-send-file"),
                enabled = !busy
            ) {
                Text(stringResource(R.string.partner_prepare_file), textAlign = TextAlign.Center)
            }
            OutlinedButton(
                bluetooth,
                Modifier.weight(1f).fillMaxHeight().testTag("partner-send-bluetooth"),
                enabled = !busy
            ) {
                Text(stringResource(R.string.partner_bluetooth), textAlign = TextAlign.Center)
            }
        }
    }
}
