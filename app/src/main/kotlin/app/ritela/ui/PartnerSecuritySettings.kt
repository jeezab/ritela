package app.ritela.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritela.R
import app.ritela.RitelaApplication

@Composable
internal fun PartnerSecuritySettings() {
    val session = LocalProfileSession.current ?: return
    val model = session.partnerModel
    val context = LocalContext.current
    val directory by model.directory.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val ownId by model.identityId.collectAsStateWithLifecycle()
    var technical by remember { mutableStateOf(false) }
    var keyTransfer by remember { mutableStateOf(false) }
    var keyRestore by remember { mutableStateOf(false) }
    var keyPassword by remember { mutableStateOf("") }
    val activeSession = {
        (context.applicationContext as RitelaApplication).profiles.session.value.token ==
            session.token
    }
    val exportKey =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            val password = keyPassword.toCharArray()
            keyPassword = ""
            if (uri != null && activeSession()) {
                model.run {
                    val bytes = model.repository.exportIdentity(password)
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use {
                        it.write(bytes)
                    }
                }
            } else {
                password.fill('\u0000')
            }
        }
    val importKey =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val password = keyPassword.toCharArray()
            keyPassword = ""
            if (uri != null && activeSession()) {
                model.run {
                    model.repository.restoreIdentity(PartnerFiles.read(context, uri), password)
                    model.identityId.value = model.repository.identity().identityId
                }
            } else {
                password.fill('\u0000')
            }
        }
    OutlinedButton({
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
        )
    }, Modifier.fillMaxWidth().testTag("partner-settings-bluetooth")) {
        Text(stringResource(R.string.partner_android_settings), textAlign = TextAlign.Center)
    }
    OutlinedButton({
        keyTransfer = true
    }, Modifier.fillMaxWidth().testTag("partner-settings-identity"), enabled = !busy) {
        Text(stringResource(R.string.partner_identity), textAlign = TextAlign.Center)
    }
    OutlinedButton({
        technical = !technical
    }, Modifier.fillMaxWidth().testTag("partner-settings-technical")) {
        Text(stringResource(R.string.partner_technical), textAlign = TextAlign.Center)
    }
    if (technical) {
        Text(ownId.chunked(8).joinToString(" "), style = MaterialTheme.typography.bodySmall)
        directory.identities.forEach { source ->
            Text(source.name)
            Text(source.id, style = MaterialTheme.typography.bodySmall)
            source.devices.forEach { Text(it.id, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (keyTransfer) {
        PartnerModal(stringResource(R.string.partner_your_devices), {
            keyPassword = ""
            keyTransfer =
                false
        }) {
            Text(stringResource(R.string.partner_identity_hint))
            OutlinedTextField(
                keyPassword,
                {
                    if (it.length <=
                        256
                    ) {
                        keyPassword = it
                    }
                },
                label = { Text(stringResource(R.string.partner_password)) },
                visualTransformation =
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
            Button(
                {
                    keyTransfer = false
                    exportKey.launch("identity.ritela")
                },
                enabled =
                    keyPassword.length >= 4 && !busy
            ) {
                Text(stringResource(R.string.partner_export_identity))
            }
            Button(
                { keyRestore = true },
                enabled =
                    keyPassword.length >= 4 && directory.identities.isEmpty() && !busy
            ) {
                Text(stringResource(R.string.partner_restore_identity))
            }
        }
    }
    if (keyRestore) {
        PartnerModal(stringResource(R.string.partner_restore_identity), {
            keyRestore =
                false
        }) {
            Text(stringResource(R.string.partner_restore_confirm))
            Button({
                keyTransfer = false
                keyRestore = false
                importKey.launch(arrayOf("*/*"))
            }) { Text(stringResource(R.string.partner_continue)) }
        }
    }
}
