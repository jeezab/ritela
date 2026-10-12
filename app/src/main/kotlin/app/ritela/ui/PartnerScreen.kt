package app.ritela.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritela.R
import app.ritela.RitelaApplication
import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerMessage
import app.ritela.domain.ExchangeEdge
import app.ritela.domain.ExchangePhase
import app.ritela.domain.ShareScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun PartnerSettings() {
    val session = LocalProfileSession.current ?: return
    val mode by session.partnerPreferences.mode.collectAsStateWithLifecycle()
    SettingsGroup {
        Text(
            stringResource(R.string.partner_application_mode),
            style = MaterialTheme.typography.titleMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                !mode.recipient,
                {
                    session.partnerModel.updatePreferences {
                        session.partnerPreferences.recipient(false)
                    }
                },
                Modifier.testTag("partner-own-mode")
            )
            Column {
                Text(stringResource(R.string.partner_my_calendar))
                Text(
                    stringResource(R.string.partner_my_calendar_hint),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                mode.recipient,
                {
                    session.partnerModel.updatePreferences {
                        session.partnerPreferences.recipient(true)
                    }
                },
                Modifier.testTag("partner-recipient")
            )
            Column {
                Text(stringResource(R.string.partner_mode))
                Text(
                    stringResource(R.string.partner_recipient_hint),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Text(stringResource(R.string.partner_mode_hint), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.partner_show), Modifier.weight(1f))
            Switch(
                mode.show || mode.recipient,
                { value ->
                    session.partnerModel.updatePreferences {
                        session.partnerPreferences.show(value)
                    }
                },
                modifier = Modifier.testTag("partner-show"),
                enabled = !mode.recipient
            )
        }
        Text(stringResource(R.string.partner_private), style = MaterialTheme.typography.bodySmall)
    }
    PartnerSecuritySettings()
}

@Composable
fun PartnerScreen(padding: PaddingValues) {
    val session = LocalProfileSession.current ?: return
    val model = session.partnerModel
    val context = LocalContext.current
    val resources = LocalResources.current
    val directory by model.directory.collectAsStateWithLifecycle()
    val messages by model.messages.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val invitation by model.invitation.collectAsStateWithLifecycle()
    val candidate by model.candidate.collectAsStateWithLifecycle()
    val packet by model.packet.collectAsStateWithLifecycle()
    val exchange by model.bluetooth.state.collectAsStateWithLifecycle()
    val activePeer by model.bluetooth.activePeer.collectAsStateWithLifecycle()
    val code by model.verificationCode.collectAsStateWithLifecycle()
    val pairingTarget by model.pairingTarget.collectAsStateWithLifecycle()
    val connectedNotice by model.connectedNotice.collectAsStateWithLifecycle()
    val legacyInvitation by model.legacyInvitation.collectAsStateWithLifecycle()
    var onboarding by remember { mutableStateOf(false) }
    val profileName = (context.applicationContext as RitelaApplication).profiles.registry.profiles
        .collectAsStateWithLifecycle().value.firstOrNull {
            it.id == session.profileId
        }?.name.orEmpty()
    val periodState by session.model.uiState.collectAsStateWithLifecycle()
    val preferences by session.partnerPreferences.mode.collectAsStateWithLifecycle()
    val selected = preferences.selected
    val scope = rememberCoroutineScope()
    var deviceSelection by remember { mutableStateOf<String?>(null) }
    var identitySelection by remember { mutableStateOf<String?>(null) }
    var manage by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var drawing by remember { mutableStateOf(false) }
    var dataViewing by remember { mutableStateOf<PartnerMessage?>(null) }
    var galleryLimit by remember { androidx.compose.runtime.mutableIntStateOf(24) }
    var viewing by remember { mutableStateOf<PartnerMessage?>(null) }
    var transport by remember { mutableStateOf(false) }
    var phones by remember { mutableStateOf(false) }
    var edge by remember { mutableStateOf(ExchangeEdge.RIGHT) }
    var showExchange by rememberSaveable { mutableStateOf(false) }
    var fileStatus by remember { mutableStateOf(false) }
    val activeSession = {
        (context.applicationContext as RitelaApplication).profiles.session.value.token ==
            session.token
    }
    var savingInvitation by remember { mutableStateOf<String?>(null) }
    val saveInvitation = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.ritela")
    ) { uri ->
        val value = savingInvitation
        savingInvitation = null
        if (uri != null && value != null && activeSession()) {
            model.run { PartnerFiles.saveInvitation(context, uri, value) }
        }
    }
    val importFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null && activeSession()) {
                model.run {
                    val bytes = PartnerFiles.read(context, uri)
                    val isInvitation = app.ritela.data.PartnerInvitationFile.decode(bytes) != null
                    model.receiveFile(bytes)
                    fileStatus = !isInvitation
                }
            }
        }
    var receiving by remember { mutableStateOf(false) }
    var pairingBluetooth by remember { mutableStateOf(false) }
    val discover =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode > 0 &&
                activeSession()
            ) {
                showExchange = true
                if (pairingBluetooth) {
                    model.exchangeInvitation(null, profileName)
                } else {
                    model.listen(context.displayRotation())
                }
            }
        }
    val btPermissions =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            if (activeSession() && grants.values.all { it }) {
                if (receiving) {
                    discover.launch(
                        Intent(
                            BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE
                        ).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                    )
                } else {
                    phones = true
                }
            } else {
                model.error.value = true
            }
        }
    fun requestBluetooth(receive: Boolean, invitationExchange: Boolean = false) {
        receiving = receive
        pairingBluetooth = invitationExchange
        if (Build.VERSION.SDK_INT >=
            31
        ) {
            btPermissions.launch(
                if (receive) {
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                    )
                } else {
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
                }
            )
        } else if (receive) {
            discover.launch(
                Intent(
                    BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE
                ).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
            )
        } else {
            phones = true
        }
    }
    LaunchedEffect(packet?.id) { if (packet != null) transport = true }
    LaunchedEffect(candidate, connectedNotice, legacyInvitation) {
        if (candidate != null || connectedNotice || legacyInvitation) showExchange = false
    }
    // Date-locked letters are evaluated again on each real application resume.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { now = System.currentTimeMillis() }
    val contact =
        directory.contacts.firstOrNull { it.id == selected } ?: directory.contacts.firstOrNull()
    val identities = directory.identities.filter { it.id in contact?.identities.orEmpty() }
    val identity = identities.firstOrNull { it.id == identitySelection } ?: identities.firstOrNull()
    val device =
        identity?.devices?.firstOrNull {
            it.id == deviceSelection && it.enabled &&
                it.mutualConfirmed
        }
            ?: identity?.devices?.firstOrNull { it.enabled && it.mutualConfirmed }
    HomeTheme {
        Column(
            Modifier.fillMaxSize().background(HomeColors.background).padding(padding)
                .verticalScroll(rememberScrollState()).padding(HomeSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(HomeSpacing.gap)
        ) {
            Text(
                stringResource(R.string.partner_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                stringResource(R.string.partner_private),
                style = MaterialTheme.typography.bodySmall
            )
            if (error) {
                Text(
                    stringResource(R.string.partner_error),
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (busy) Text(stringResource(R.string.loading))
            if (directory.contacts.isEmpty()) {
                PartnerWelcome { onboarding = true }
            }
            if (directory.contacts.isEmpty()) {
                PartnerReceiveCard(!busy, { importFile.launch(arrayOf("*/*")) }, {
                    requestBluetooth(true)
                })
            }
            directory.contacts.forEach { item ->
                TextButton({
                    scope.launch { session.partnerPreferences.select(item.id) }
                    identitySelection = null
                }, Modifier.fillMaxWidth()) {
                    RadioButton(item.id == contact?.id, null)
                    Text(item.name, Modifier.weight(1f))
                    if (item.group) Text(stringResource(R.string.partner_group))
                }
            }
            contact?.let { item ->
                SettingsGroup {
                    PartnerContactHeading(
                        item.name,
                        stringResource(
                            if (identity?.exchangeEnabled == true &&
                                device != null
                            ) {
                                if (activePeer in
                                    item.identities
                                ) {
                                    R.string.partner_connected
                                } else {
                                    R.string.partner_unconnected
                                }
                            } else if (identity?.exchangeEnabled ==
                                true
                            ) {
                                R.string.partner_pending_connection
                            } else {
                                R.string.partner_disabled
                            }
                        )
                    )
                    if (identities.size > 1) {
                        identities.forEach { source ->
                            TextButton({
                                identitySelection = source.id
                            }) { Text(source.name) }
                        }
                    }
                    identity?.let { source ->
                        messages.firstOrNull {
                            it.identity == source.id && (!it.outgoing || it.delivered || it.opened)
                        }?.let {
                            Text(
                                PartnerFormatter(resources).exchange(it.received),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        FlowRow {
                            Button(
                                { sharing = true },
                                enabled =
                                    !busy && !item.group && device != null && source.exchangeEnabled
                            ) {
                                Text(stringResource(R.string.partner_exchange))
                            }
                            TextButton({
                                manage = true
                            }, enabled = !busy) { Text(stringResource(R.string.partner_manage)) }
                        }
                        val latest = messages.filter {
                            !it.outgoing && it.identity == source.id &&
                                it.kind == "DATA" &&
                                it.body.isNotEmpty()
                        }.firstOrNull()
                        latest?.let { message ->
                            PartnerActionCard(
                                stringResource(R.string.partner_close_data),
                                stringResource(R.string.partner_readonly)
                            ) {
                                dataViewing =
                                    message
                            }
                        }
                        if (latest == null) {
                            PartnerActionCard(
                                stringResource(R.string.partner_close_data),
                                stringResource(R.string.partner_no_received_data),
                                false
                            ) {
                            }
                        }
                        if (source.exchangeEnabled &&
                            (
                                device == null ||
                                    source.devices.any { it.enabled && !it.mutualConfirmed }
                                )
                        ) {
                            Text(stringResource(R.string.partner_pending_hint))
                            Button({
                                if (source.devices.any { it.enabled && !it.mutualConfirmed }) {
                                    model.resumePairing(source.id, profileName)
                                } else {
                                    importFile.launch(
                                        arrayOf(
                                            "application/octet-stream",
                                            "application/vnd.ritela"
                                        )
                                    )
                                }
                            }, enabled = !busy) {
                                Text(stringResource(R.string.partner_finish_connection))
                            }
                        }
                        PartnerActionCard(
                            stringResource(R.string.partner_doodle),
                            stringResource(R.string.partner_doodle_hint),
                            !busy && !item.group && device != null && source.exchangeEnabled
                        ) {
                            drawing =
                                true
                        }
                    }
                }
                Text(
                    stringResource(R.string.partner_gallery),
                    style = MaterialTheme.typography.titleMedium
                )
                val gallery = messages.filter {
                    it.identity in item.identities &&
                        it.kind == "DOODLE" &&
                        it.body.isNotEmpty()
                }
                gallery.take(galleryLimit).forEach { message ->
                    SettingsGroup {
                        val senderName = if (message.outgoing) {
                            profileName
                        } else {
                            directory.identities.firstOrNull {
                                it.id ==
                                    message.identity
                            }?.name.orEmpty()
                        }
                        Text(senderName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            PartnerFormatter(resources).message(message),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (message.kind == "DOODLE") {
                            val doodle =
                                remember(message.body) {
                                    PartnerCodec.doodle(JSONObject(message.body))
                                }
                            if (message.outgoing || message.opened) {
                                androidx.compose.foundation.Canvas(
                                    Modifier.fillMaxWidth().heightIn(min = 130.dp)
                                        .graphicsLayer {
                                            compositingStrategy =
                                                CompositingStrategy.Offscreen
                                        }
                                ) {
                                    drawDoodle(doodle.strokes)
                                }
                                if (doodle.caption.isNotBlank()) Text(doodle.caption)
                            }
                            if (message.outgoing || message.opened ||
                                doodle.canOpen(message.received, now)
                            ) {
                                TextButton({
                                    viewing = message
                                    if (!message.outgoing) {
                                        model.run {
                                            model.repository.openDoodle(message.id)?.let {
                                                model.packet.value =
                                                    it
                                            }
                                        }
                                    }
                                }) { Text(stringResource(R.string.partner_open_doodle)) }
                            } else {
                                Text(
                                    PartnerFormatter(
                                        LocalResources.current
                                    ).locked(doodle.unlockAt(message.received))
                                )
                            }
                        }
                        if (message.outgoing &&
                            !message.delivered
                        ) {
                            TextButton({
                                model.run {
                                    model.packet.value =
                                        model.repository.retry(message.id)
                                }
                            }, enabled = !busy) { Text(stringResource(R.string.partner_retry)) }
                        }
                    }
                }
            }
            if (messages.count {
                    it.identity in contact?.identities.orEmpty() &&
                        it.kind == "DOODLE" &&
                        it.body.isNotEmpty()
                } >
                galleryLimit
            ) {
                TextButton({ galleryLimit += 24 }) { Text(stringResource(R.string.partner_more)) }
            }
            if (directory.contacts.isNotEmpty()) {
                PartnerConnectionCard(!busy, {
                    model.createInvitation(profileName, fresh = true)
                }, {
                    importFile.launch(arrayOf("application/octet-stream", "application/vnd.ritela"))
                })
                PartnerReceiveCard(!busy, { importFile.launch(arrayOf("*/*")) }, {
                    requestBluetooth(true)
                })
            }
            Text(
                stringResource(R.string.partner_limits),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (onboarding) {
            PartnerModal(stringResource(R.string.partner_add_close), {
                onboarding =
                    false
            }) {
                Text(stringResource(R.string.partner_onboarding_steps))
                Button({
                    onboarding = false
                    model.createInvitation(profileName, fresh = true)
                }, enabled = !busy) { Text(stringResource(R.string.partner_invite)) }
                Button({
                    onboarding = false
                    importFile.launch(arrayOf("application/octet-stream", "application/vnd.ritela"))
                }, enabled = !busy) { Text(stringResource(R.string.partner_open_invitation)) }
            }
        }
        if (connectedNotice) {
            PartnerModal(stringResource(R.string.partner_linked), {
                model.connectedNotice.value =
                    false
            }) {
                Text(stringResource(R.string.partner_connected_body))
            }
        }
        if (legacyInvitation) {
            PartnerModal(stringResource(R.string.partner_code_label), {
                model.legacyInvitation.value =
                    false
            }) {
                Text(stringResource(R.string.partner_update_needed))
            }
        }
        invitation?.let { value ->
            PartnerModal(stringResource(R.string.partner_invite), {
                model.invitation.value = null
            }) {
                PartnerInvitationActions(
                    pairingTarget != null,
                    !busy,
                    save = {
                        savingInvitation = value
                        saveInvitation.launch("ritela-invitation.ritela")
                    },
                    share = { PartnerFiles.shareInvitation(context, session.profileId, value) },
                    open = {
                        importFile.launch(
                            arrayOf("application/octet-stream", "application/vnd.ritela")
                        )
                    },
                    sendBluetooth = { requestBluetooth(false, true) },
                    receiveBluetooth = { requestBluetooth(true, true) }
                )
                if (pairingTarget != null) {
                    Text(code, style = MaterialTheme.typography.headlineLarge)
                    val complete = directory.identities.firstOrNull {
                        it.id == pairingTarget?.identity
                    }?.devices?.firstOrNull {
                        it.id == pairingTarget?.device?.id
                    }?.mutualConfirmed == true
                    Text(
                        stringResource(
                            if (complete) R.string.partner_linked else R.string.partner_pending_hint
                        )
                    )
                }
            }
        }
        candidate?.let { invite ->
            PartnerModal(stringResource(R.string.partner_verify_title), {
                model.candidate.value =
                    null
            }) {
                PartnerVerificationContent(
                    invite.name,
                    code,
                    invite.identity,
                    !busy,
                    { model.pair(profileName) },
                    { model.candidate.value = null }
                )
                Text(stringResource(R.string.partner_verification_invitation_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({
                        model.run {
                            val value = model.repository.invitation(profileName)
                            withContext(Dispatchers.Main) {
                                savingInvitation = value
                                saveInvitation.launch("ritela-invitation.ritela")
                            }
                        }
                    }, enabled = !busy) { Text(stringResource(R.string.partner_save_invitation)) }
                    TextButton({
                        model.run {
                            val value = model.repository.invitation(profileName)
                            withContext(Dispatchers.Main) {
                                PartnerFiles.shareInvitation(context, session.profileId, value)
                            }
                        }
                    }, enabled = !busy) { Text(stringResource(R.string.partner_share_invitation)) }
                }
            }
        }
        identity?.let { source ->
            if (sharing &&
                device != null
            ) {
                PartnerScopeDialog(periodState.journalLayout, source.grants, ShareScope(), false, {
                    sharing =
                        false
                }, source.name) { selection ->
                    model.data(source.id, device.id, selection)
                    sharing = false
                }
            }
            if (drawing &&
                device != null
            ) {
                DoodleEditor({ drawing = false }) { doodle ->
                    model.doodle(source.id, device.id, doodle)
                    drawing =
                        false
                }
            }
            if (manage &&
                contact != null
            ) {
                PartnerManagement(model, contact, source, { manage = false })
            }
        }
        dataViewing?.let { message ->
            PartnerModal(stringResource(R.string.partner_data), {
                dataViewing =
                    null
            }) {
                ReadOnlyPartnerData(message.body)
            }
        }
        viewing?.let { DoodleViewer(PartnerCodec.doodle(JSONObject(it.body)), { viewing = null }) }
        if (transport &&
            packet != null
        ) {
            PartnerModal(stringResource(R.string.partner_transport), {
                transport =
                    false
            }) {
                Text(PartnerFormatter(resources).message(requireNotNull(packet)))
                PartnerFileGlow()
                Button({
                    model.run {
                        val outgoing = model.repository.retry(requireNotNull(packet).id)
                        withContext(Dispatchers.Main) {
                            PartnerFiles.share(context, session.profileId, outgoing)
                        }
                    }
                    transport = false
                }) { Text(stringResource(R.string.partner_share_file)) }
                Button({
                    transport = false
                    requestBluetooth(false)
                }, enabled = !busy) { Text(stringResource(R.string.partner_bluetooth)) }
            }
        }
        if (phones) {
            PartnerModal(stringResource(R.string.partner_where), { phones = false }) {
                Text(
                    stringResource(
                        if (pairingBluetooth) {
                            R.string.partner_invitation_bluetooth_hint
                        } else {
                            R.string.partner_pair_android
                        }
                    )
                )
                if (!pairingBluetooth) {
                    FlowRow {
                        ExchangeEdge.entries.forEach { side ->
                            TextButton({ edge = side }) {
                                Text(
                                    (
                                        if (edge ==
                                            side
                                        ) {
                                            "• "
                                        } else {
                                            ""
                                        }
                                        ) + stringResource(PartnerFormatter.edge(side))
                                )
                            }
                        }
                    }
                }
                val nearby = runCatching { model.bluetooth.phones() }.getOrDefault(emptyList())
                nearby.forEach { phone ->
                    Button(
                        {
                            phones = false
                            showExchange = true
                            if (pairingBluetooth) {
                                model.exchangeInvitation(phone.address, profileName)
                            } else {
                                model.send(phone.address, edge, context.displayRotation())
                            }
                        },
                        enabled =
                            !busy && (pairingBluetooth || packet != null)
                    ) { Text(phone.name) }
                }
                TextButton({
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                    )
                }) { Text(stringResource(R.string.partner_android_settings)) }
            }
        }
        if (showExchange) {
            PartnerExchangeScene(exchange, {
                model.cancelExchange()
                showExchange =
                    false
            })
        }
        if (fileStatus) {
            PartnerModal(stringResource(R.string.partner_received), {
                fileStatus = false
            }) {
                PartnerFileGlow()
                Text(stringResource(R.string.partner_file_received))
            }
        }
    }
}

internal fun android.content.Context.displayRotation(): Int = if (Build.VERSION.SDK_INT >= 30) {
    display?.rotation ?: 0
} else {
    legacyRotation()
}

@Suppress("DEPRECATION")
private fun android.content.Context.legacyRotation(): Int =
    getSystemService(android.view.WindowManager::class.java).defaultDisplay.rotation

@Composable
internal fun PartnerModal(
    title: String,
    dismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val maximumHeight =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height.toDp() *
                0.86f
        }
    JournalDialog(dismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().safeDrawingPadding().imePadding().padding(16.dp)
                .heightIn(max = maximumHeight),
            shape = MaterialTheme.shapes.large,
            color = HomeColors.card
        ) {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(dismiss) { Text(stringResource(R.string.partner_close)) }
                }
                content()
            }
        }
    }
}
