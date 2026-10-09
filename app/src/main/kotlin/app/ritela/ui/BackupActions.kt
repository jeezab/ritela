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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritela.R
import app.ritela.RitelaApplication
import app.ritela.data.BackupCategory
import app.ritela.data.BackupData
import app.ritela.data.BackupRepository
import app.ritela.data.BackupSelection
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupActions() {
    val context = LocalContext.current
    val manager = (context.applicationContext as RitelaApplication).profiles
    val activeSession by manager.session.collectAsStateWithLifecycle()
    val session = LocalProfileSession.current ?: activeSession
    val repository = session.backups
    val users by manager.registry.profiles.collectAsStateWithLifecycle()
    val switching by manager.switching.collectAsStateWithLifecycle()
    val user = users.firstOrNull { it.id == session.profileId }?.name
        ?: app.ritela.data.ProfileRegistry.DEFAULT_NAME
    fun begin(): Boolean {
        if (manager.switching.value || manager.session.value !== session ||
            session.model.uiState.value.saving
        ) {
            return false
        }
        session.backupActive.value = true
        return true
    }
    fun release() {
        session.backupActive.value = false
    }
    var exportChoices by remember { mutableStateOf(false) }
    var exportSelection by remember { mutableStateOf(BackupSelection()) }
    var documentSession by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }
    var passwordMode by remember { mutableStateOf<String?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<BackupData?>(null) }
    var result by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            exportPassword?.fill('\u0000')
            release()
        }
    }
    val exportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val target = documentSession
        documentSession = null
        val password = exportPassword
        exportPassword = null
        if (uri == null || password == null || manager.session.value !== session ||
            target != session.token
        ) {
            password?.fill('\u0000')
            release()
        } else {
            busy = true
            scope.launch {
                try {
                    val bytes = repository.export(password, exportSelection)
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
                    release()
                }
            }
        }
    }
    val importFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val target = documentSession
            documentSession = null
            if (uri != null && manager.session.value === session && target == session.token) {
                importUri = uri
                passwordMode = "import"
                result = null
            } else {
                release()
            }
        }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleLarge)
        TextButton(
            onClick = {
                if (begin()) exportChoices = true
                result = null
            },
            enabled = !busy && !switching,
            modifier = Modifier.testTag("backup-export")
        ) {
            Icon(painterResource(R.drawable.ic_export), contentDescription = null)
            Text(stringResource(R.string.backup_export), Modifier.padding(start = Spacing.small))
        }
        TextButton(
            onClick = {
                if (begin()) {
                    documentSession = session.token
                    importFile.launch(arrayOf("*/*"))
                }
            },
            enabled = !busy && !switching,
            modifier = Modifier.testTag("backup-import")
        ) {
            Icon(painterResource(R.drawable.ic_import), contentDescription = null)
            Text(stringResource(R.string.backup_import), Modifier.padding(start = Spacing.small))
        }
        if (busy) Text(stringResource(R.string.loading))
        result?.let { Text(stringResource(it), modifier = Modifier.testTag("backup-result")) }
    }
    if (exportChoices) {
        BackupChoices(
            true,
            user,
            BackupCategory.entries.toSet(),
            onDismiss = {
                exportChoices = false
                release()
            },
            onConfirm = {
                exportSelection = it
                exportChoices = false
                passwordMode = "export"
            }
        )
    }
    passwordMode?.let { mode ->
        BackupPasswordDialog(exporting = mode == "export", onDismiss = {
            passwordMode = null
            importUri = null
            release()
        }) { password ->
            passwordMode = null
            if (mode == "export") {
                exportPassword = password
                documentSession = session.token
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
                        if (preview == null) release()
                    }
                }
            }
        }
    }
    preview?.let { data ->
        BackupChoices(
            false,
            user,
            data.available,
            data,
            busy,
            result,
            onDismiss = {
                preview = null
                release()
            },
            onConfirm = { selection ->
                busy = true
                result = null
                scope.launch {
                    try {
                        check(manager.session.value === session)
                        repository.import(data, selection)
                        result = R.string.backup_imported
                        preview = null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        result = R.string.backup_conflict
                    } finally {
                        busy = false
                        if (preview == null) release()
                    }
                }
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
