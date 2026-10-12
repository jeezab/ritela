package app.ritela.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritela.R
import kotlinx.coroutines.flow.MutableStateFlow

class PartnerFileRequest {
    val uri = MutableStateFlow<Uri?>(null)
    fun inspect(intent: Intent?) {
        val value = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> stream(intent)
            else -> null
        }
        uri.value = value?.takeIf { it.scheme == "content" }
    }

    @Suppress("DEPRECATION")
    private fun stream(intent: Intent): Uri? = if (android.os.Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }
}

val LocalPartnerFileRequest = staticCompositionLocalOf<PartnerFileRequest?> { null }

@Composable
internal fun PartnerExternalImport() {
    val request = LocalPartnerFileRequest.current ?: return
    val uri by request.uri.collectAsStateWithLifecycle()
    val session = LocalProfileSession.current ?: return
    val context = LocalContext.current
    uri?.let { file ->
        AlertDialog(
            onDismissRequest = { request.uri.value = null },
            title = { Text(stringResource(R.string.partner_import)) },
            text = { Text(stringResource(R.string.partner_file_confirm)) },
            confirmButton = {
                TextButton({
                    request.uri.value = null
                    session.partnerModel.run {
                        val received = session.partner.receive(PartnerFiles.read(context, file))
                        if (received.message.kind in listOf("DATA", "DOODLE")) {
                            session.partnerModel.packet.value =
                                session.partner.acknowledgement(received.message)
                        }
                    }
                }) { Text(stringResource(R.string.partner_continue)) }
            },
            dismissButton = {
                TextButton({ request.uri.value = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
