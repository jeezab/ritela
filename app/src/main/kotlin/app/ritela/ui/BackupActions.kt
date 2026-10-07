package app.ritela.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ritela.R
import app.ritela.RitelaApplication
import app.ritela.data.BackupData
import app.ritela.data.BackupRepository
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupActions() {
    val context = LocalContext.current
    val repository = (context.applicationContext as RitelaApplication).backups
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }
    var passwordMode by remember { mutableStateOf<String?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<BackupData?>(null) }
    var result by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(Unit) {
        onDispose { exportPassword?.fill('\u0000') }
    }
    val exportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val password = exportPassword
        exportPassword = null
        if (uri == null || password == null) {
            password?.fill('\u0000')
        } else {
            busy = true
            scope.launch {
                try {
                    val bytes = repository.export(password)
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                            .use { it.write(bytes) }
                    }
                    result = R.string.backup_exported
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    result = R.string.backup_error
                } finally {
                    password.fill('\u0000')
                    busy = false
                }
            }
        }
    }
    val importFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                importUri = uri
                passwordMode = "import"
                result = null
            }
        }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleLarge)
        TextButton(
            onClick = {
                passwordMode = "export"
                result = null
            },
            enabled = !busy,
            modifier = Modifier.testTag("backup-export")
        ) {
            Icon(painterResource(R.drawable.ic_export), contentDescription = null)
            Text(stringResource(R.string.backup_export), Modifier.padding(start = Spacing.small))
        }
        TextButton(
            onClick = { importFile.launch(arrayOf("*/*")) },
            enabled = !busy,
            modifier = Modifier.testTag("backup-import")
        ) {
            Icon(painterResource(R.drawable.ic_import), contentDescription = null)
            Text(stringResource(R.string.backup_import), Modifier.padding(start = Spacing.small))
        }
        if (busy) Text(stringResource(R.string.loading))
        result?.let { Text(stringResource(it), modifier = Modifier.testTag("backup-result")) }
    }
    passwordMode?.let { mode ->
        BackupPasswordDialog(exporting = mode == "export", onDismiss = {
            passwordMode = null
            importUri = null
        }) { password ->
            passwordMode = null
            if (mode == "export") {
                exportPassword = password
                exportFile.launch("Ritela-${LocalDate.now()}.ritela")
            } else {
                val uri = importUri
                importUri = null
                busy = true
                scope.launch {
                    try {
                        val bytes = withContext(Dispatchers.IO) {
                            requireNotNull(
                                context.contentResolver.openInputStream(requireNotNull(uri))
                            )
                                .use { input ->
                                    val output = ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        require(
                                            output.size() + count <= BackupRepository.MAX_FILE_BYTES
                                        )
                                        output.write(buffer, 0, count)
                                    }
                                    output.toByteArray()
                                }
                        }
                        preview = repository.preview(bytes, password)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        result = R.string.backup_error
                    } finally {
                        password.fill('\u0000')
                        busy = false
                    }
                }
            }
        }
    }
    preview?.let { data ->
        AlertDialog(
            onDismissRequest = { if (!busy) preview = null },
            title = { Text(stringResource(R.string.backup_import)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                    Text(stringResource(R.string.backup_preview, data.periods.size, data.logs.size))
                    Text(stringResource(R.string.backup_merge_hint))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    busy = true
                    scope.launch {
                        try {
                            repository.import(data)
                            result = R.string.backup_imported
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            result = R.string.backup_conflict
                        } finally {
                            preview = null
                            busy = false
                        }
                    }
                }, enabled = !busy, modifier = Modifier.testTag("backup-confirm")) {
                    Text(stringResource(R.string.backup_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    preview = null
                }, enabled = !busy) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    exporting: Boolean,
    onDismiss: () -> Unit,
    onContinue: (CharArray) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.padding(Spacing.large),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                Text(
                    stringResource(R.string.backup_password),
                    style = MaterialTheme.typography.headlineMedium
                )
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    Text(
                        stringResource(
                            if (exporting) {
                                R.string.backup_password_hint
                            } else {
                                R.string.backup_open_hint
                            }
                        )
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("backup-password"),
                        label = { Text(stringResource(R.string.backup_password)) }
                    )
                    if (exporting) {
                        OutlinedTextField(
                            value = repeat,
                            onValueChange = { repeat = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().testTag("backup-repeat"),
                            label = { Text(stringResource(R.string.backup_repeat)) }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(
                        onClick = {
                            val chars = password.toCharArray()
                            password = ""
                            repeat = ""
                            onContinue(chars)
                        },
                        enabled = if (exporting) {
                            password == repeat
                        } else {
                            true
                        },
                        modifier = Modifier.testTag("backup-continue")
                    ) {
                        Text(stringResource(R.string.backup_continue))
                    }
                }
            }
        }
    }
}
