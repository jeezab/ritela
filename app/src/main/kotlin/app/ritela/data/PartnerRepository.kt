package app.ritela.data

import androidx.room.withTransaction
import app.ritela.data.sync.SyncApplyRepository
import app.ritela.data.sync.SyncAuthorGrants
import app.ritela.data.sync.SyncCapabilityRow
import app.ritela.data.sync.SyncDraft
import app.ritela.data.sync.SyncEnvelopeCodec
import app.ritela.domain.Doodle
import app.ritela.domain.EstimatedCyclePhase
import app.ritela.domain.JournalLayout
import app.ritela.domain.PartnerDevice
import app.ritela.domain.PartnerDirectory
import app.ritela.domain.PartnerIdentity
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import app.ritela.domain.analyzeCycles
import app.ritela.domain.cycleSceneState
import app.ritela.domain.historySections
import app.ritela.domain.journalSelections
import app.ritela.domain.sync.SyncLimits
import app.ritela.domain.validJournalId
import java.security.KeyPair
import java.time.LocalDate
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class PairInvitation(
    val name: String,
    val identity: String,
    val key: String,
    val device: PartnerDevice,
    val version: Int = 2,
    val confirmation: String? = null,
    val signedInvitation: String = ""
)
data class ReceivedPartnerPacket(val message: PartnerMessage, val duplicate: Boolean)

/** Contacts/grants and inbox/outbox are scoped to the existing user's Room database. */
class PartnerRepository(
    private val database: RitelaDatabase,
    private val loadKeys: () -> LocalPartnerKeys,
    private val saveKeys: (LocalPartnerKeys) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val dao = database.partner()
    val directory = dao.directory().map {
        it?.let { PartnerCodec.directory(it.json) }
            ?: PartnerDirectory()
    }
    private val syncApply = SyncApplyRepository(database, ::validateData)
    val messages = combine(dao.messages(), database.syncReceive().states()) { history, states ->
        history + states.map {
            PartnerMessage(
                "sync-projection:${it.identity}", it.identity, "", false, "SYNC_PROJECTION",
                it.received, it.received, it.snapshot, "", delivered = true, hiddenFromChat = true
            )
        }
    }

    /** Explicit manual exchange only; no network or automatic opt-in. */
    suspend fun prepareSyncCapabilities(identity: String, device: String): PartnerMessage =
        database.withTransaction {
            val remote = snapshot().identities.single { it.id == identity }.devices.single {
                it.id == device && it.enabled && it.mutualConfirmed
            }
            val context = "${remote.localNonce}:${remote.remoteNonce}"
            val previous = database.syncReceive().capability(identity, device)
            val capabilities = SyncEnvelopeCodec.capabilities()
                .put("senderNonce", remote.localNonce).put("recipientNonce", remote.remoteNonce)
            val result = prepare(identity, device, "SYNC_CAPS", capabilities)
            database.syncReceive().capability(
                SyncCapabilityRow(
                    identity,
                    device,
                    context,
                    true,
                    previous?.context == context && previous.accepted
                )
            )
            result
        }

    suspend fun initializeSyncAuthor(): String = SyncAuthorGrants(database).initialize(identity())

    suspend fun approveSyncAuthor(identity: String, grant: String) {
        database.withTransaction {
            syncApply.approveAuthor(
                snapshot().identities.single { it.id == identity && it.exchangeEnabled },
                grant
            )
        }
    }

    /** Encoder for the queue and explicit manual adapters; never invoked on profile open. */
    suspend fun encodeSyncDraft(draft: SyncDraft): ByteArray {
        val keys = identity()
        return database.withTransaction {
            requireSyncCapabilities(draft.peerIdentity, draft.peerDevice)
            val state = database.syncState().authorities().single()
            check(
                state.state == "ACTIVE" && state.ownerIdentity == keys.identityId &&
                    state.authorDevice == keys.deviceId
            )
            val content = SyncEnvelopeCodec.encode(
                draft,
                state.generation,
                requireNotNull(state.grantDigest)
            )
            val scope = PartnerCodec.scope(content.getJSONObject("scope"))
            require(
                scope.categories.isNotEmpty() && snapshot().identities.single {
                    it.id == draft.peerIdentity
                }.grants.permits(scope)
            )
            SyncEnvelopeCodec.decode(content)
            prepare(
                draft.peerIdentity,
                draft.peerDevice,
                "SYNC",
                content,
                packetId = draft.packetId
            ).envelope.toByteArray().also { require(it.size <= SyncLimits.ENVELOPE_BYTES) }
        }
    }

    private suspend fun requireSyncCapabilities(identity: String, device: String) {
        val remote = snapshot().identities.single { it.id == identity && it.exchangeEnabled }
            .devices.single { it.id == device && it.enabled && it.mutualConfirmed }
        val capability = database.syncReceive().capability(identity, device)
        check(
            capability != null && capability.offered && capability.accepted &&
                capability.context == "${remote.localNonce}:${remote.remoteNonce}"
        )
    }

    /** Keep the row ID to make re-import idempotent, even after contents were erased. */
    suspend fun deleteMessage(id: String, eraseSharedData: Boolean = false) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                database.withTransaction {
                    val message = requireNotNull(dao.message(id))
                    require(message.kind in setOf("DATA", "DOODLE"))
                    rememberDoodle(message)
                    if (eraseSharedData || !message.hasSharedData()) dao.erase(id) else dao.hide(id)
                }
            }
        }
    private suspend fun rememberDoodle(message: PartnerMessage) {
        if (message.kind == "DOODLE" || (
                message.kind == "DATA" &&
                    message.body.isNotEmpty() && JSONObject(message.body).has("doodle")
                )
        ) {
            database.syncReceive().doodle(app.ritela.data.sync.SyncDoodleReceiptRow(message.id))
        }
    }
    suspend fun identity(): LocalPartnerKeys = withContext(Dispatchers.IO) { loadKeys() }
    suspend fun snapshot(): PartnerDirectory =
        dao.snapshot()?.let { PartnerCodec.directory(it.json) } ?: PartnerDirectory()
    private suspend fun mutate(change: (PartnerDirectory) -> PartnerDirectory) = mutex.withLock {
        database.withTransaction {
            dao.save(PartnerDirectoryRow(json = PartnerCodec.directory(change(snapshot()))))
        }
    }
    suspend fun invitation(name: String, target: PairInvitation? = null): String {
        val keys = identity()
        val confirmed = target?.let { invite ->
            snapshot().identities.firstOrNull { it.id == invite.identity }?.devices
                ?.firstOrNull { it.id == invite.device.id && it.enabled }?.verificationContext
        }
        return keys.invitation(name, confirmed)
    }
    suspend fun inspectInvitation(invitation: String): PairInvitation =
        withContext(Dispatchers.IO) {
            require(invitation.length <= 12000)
            val envelope = JSONObject(invitation)
            val raw = envelope.getString("body")
            val body = JSONObject(raw)
            val version = body.getInt("v")
            require(body.getString("format") == "ritela.pair" && version in 1..2)
            val key = body.getString("identity")
            val id = PartnerCrypto.id(key)
            val deviceId = body.getString("device").also {
                require(UUID.fromString(it).toString() == it)
            }
            val deviceKey = body.getString("key")
            val certificate = body.getString("certificate")
            val nonce = body.getString("nonce").also {
                require(UUID.fromString(it).toString() == it)
            }
            require(id != identity().identityId || deviceId != identity().deviceId)
            require(
                PartnerCrypto.verify(
                    PartnerCrypto.publicKey(key),
                    "$id:$deviceId:$deviceKey",
                    certificate
                )
            )
            require(
                PartnerCrypto.verify(
                    PartnerCrypto.publicKey(deviceKey, "EC"),
                    raw,
                    envelope.getString("signature")
                )
            )
            val name = body.getString("name").also { require(it.length in 1..80) }
            val known = snapshot().identities.firstOrNull { it.id == id }
            require(known == null || known.publicKey == key)
            known?.devices?.firstOrNull {
                it.id == deviceId
            }?.let { require(it.publicKey == deviceKey) }
            PairInvitation(
                name,
                id,
                key,
                PartnerDevice(deviceId, deviceKey, certificate, identity().nonce, nonce),
                version,
                body.optString("confirmed").takeIf { it.isNotEmpty() },
                invitation
            )
        }

    /** Only explicit local consent creates trust; a signed full-context receipt proves peer consent. */
    suspend fun pair(invitation: PairInvitation) {
        require(invitation == inspectInvitation(invitation.signedInvitation))
        require(invitation.version == 2)
        require(PartnerCrypto.id(invitation.key) == invitation.identity)
        require(
            PartnerCrypto.verify(
                PartnerCrypto.publicKey(invitation.key),
                "${invitation.identity}:${invitation.device.id}:${invitation.device.publicKey}",
                invitation.device.certificate
            )
        )
        val keys = identity()
        val nonce = keys.nonce
        val context = pairingContext(keys, invitation)
        require(invitation.confirmation == null || invitation.confirmation == context)
        mutate { directory ->
            require(
                directory.identities.size < 64 ||
                    directory.identities.any { it.id == invitation.identity }
            )
            directory.add(
                PartnerIdentity(
                    invitation.identity,
                    invitation.key,
                    invitation.name,
                    listOf(
                        invitation.device.copy(
                            localNonce = nonce,
                            verificationContext = context,
                            mutualConfirmed =
                                invitation.confirmation == context
                        )
                    )
                )
            )
        }
    }

    suspend fun verificationCode(invitation: PairInvitation): String =
        pairingCode(pairingContext(identity(), invitation))

    /** Known devices are checked automatically, without resetting grants or creating duplicates. */
    suspend fun acceptKnown(invitation: PairInvitation): Boolean {
        val known =
            snapshot().identities.firstOrNull { it.id == invitation.identity && it.exchangeEnabled }
                ?: return false
        val device =
            known.devices.firstOrNull { it.id == invitation.device.id && it.enabled }
                ?: return false
        if (device.publicKey != invitation.device.publicKey ||
            device.remoteNonce != invitation.device.remoteNonce
        ) {
            return false
        }
        if (device.verificationContext == null) return device.mutualConfirmed
        if (device.verificationContext != pairingContext(identity(), invitation)) return false
        if (device.mutualConfirmed) return true
        if (invitation.confirmation != device.verificationContext) return false
        pair(invitation)
        return true
    }

    suspend fun grant(identity: String, scope: ShareScope) = mutate { directory ->
        require(
            directory.identities.single { it.id == identity }.devices.any {
                it.enabled &&
                    it.mutualConfirmed
            }
        )
        directory.copy(
            identities = directory.identities.map {
                if (it.id == identity) it.copy(grants = scope.normalized()) else it
            }
        )
    }
    suspend fun enable(identity: String, value: Boolean) = mutate { directory ->
        directory.copy(
            identities = directory.identities.map {
                if (it.id == identity) it.copy(exchangeEnabled = value) else it
            }
        )
    }
    suspend fun enableDevice(identity: String, device: String, value: Boolean) {
        if (!value) {
            withContext(Dispatchers.IO) {
                saveKeys(loadKeys().copy(nonce = UUID.randomUUID().toString()))
            }
        }
        mutate { directory ->
            directory.copy(
                identities = directory.identities.map {
                    if (it.id ==
                        identity
                    ) {
                        it.copy(
                            devices = it.devices.map { d ->
                                if (d.id ==
                                    device
                                ) {
                                    require(!value || d.mutualConfirmed)
                                    d.copy(
                                        enabled = value,
                                        mutualConfirmed = if (value) d.mutualConfirmed else false
                                    )
                                } else {
                                    d
                                }
                            }
                        )
                    } else {
                        it
                    }
                }
            )
        }
    }
    suspend fun rename(contact: String, name: String) = mutate { directory ->
        require(name.trim().length in 1..80)
        require(directory.contacts.any { it.id == contact })
        directory.copy(
            contacts = directory.contacts.map {
                if (it.id == contact) it.copy(name = name.trim()) else it
            }
        )
    }
    suspend fun merge(a: String, b: String, name: String, group: Boolean) =
        mutate { it.merge(a, b, name, group) }
    suspend fun split(contact: String) = mutate { it.split(contact) }
    suspend fun remove(contact: String) {
        withContext(Dispatchers.IO) {
            saveKeys(loadKeys().copy(nonce = UUID.randomUUID().toString()))
        }
        mutate { it.remove(contact) }
    }
    suspend fun clearReceived(identity: String) {
        database.withTransaction {
            dao.clearReceived(identity)
            syncApply.clear(identity)
        }
    }

    suspend fun preview(scope: ShareScope): JSONObject = database.withTransaction {
        val selection = app.ritela.domain.PartnerSharing.outgoing(scope)
        val json = JSONObject().put("scope", PartnerCodec.scope(selection))
        val periods = database.periods().snapshot()
        val history = ShareCategory.PERIODS in selection.categories
        if (history || ShareCategory.FORECASTS in selection.categories) {
            val sharedPeriods = if (history) {
                periods
            } else {
                listOfNotNull(
                    periods.maxByOrNull {
                        it.startDay
                    }
                )
            }
            json.put(
                "periods",
                JSONArray(
                    sharedPeriods.map {
                        JSONObject().put("id", it.id).put("start", it.startDay).put(
                            "end",
                            it.endDay ?: JSONObject.NULL
                        )
                            .put("updated", it.updatedAt)
                    }
                )
            )
        }
        if (ShareCategory.FORECASTS in selection.categories) {
            val analysis = analyzeCycles(periods.map { it.toPeriod() }, LocalDate.now())
            json.put(
                "forecasts",
                JSONArray(
                    analysis.forecasts.map {
                        JSONObject().put(
                            "start",
                            it.predictedStartDate.toEpochDay()
                        ).put("lower", it.lowerBound.toEpochDay())
                            .put(
                                "upper",
                                it.upperBound.toEpochDay()
                            ).put("ovulation", it.predictedStartDate.minusDays(14).toEpochDay())
                            .put("ovulationLower", it.lowerBound.minusDays(16).toEpochDay())
                            .put("ovulationUpper", it.upperBound.minusDays(10).toEpochDay())
                    }
                )
            ).put(
                "cycleDay",
                analysis.cycleDay ?: JSONObject.NULL
            ).put("duration", analysis.periodDuration)
                .put("phase", cycleSceneState(analysis, LocalDate.now()).phase.name)
                .put("asOf", LocalDate.now().toEpochDay())
                .put("calculatedAt", System.currentTimeMillis())
                .put("algorithm", "ritela-local-1")
                .put(
                    "cycleLength",
                    analysis.forecasts.firstOrNull()?.cycleMedian ?: JSONObject.NULL
                )
        }
        val diary = ShareCategory.DIARY in selection.categories
        if (diary || ShareCategory.SECTIONS in selection.categories ||
            ShareCategory.TAGS in selection.categories
        ) {
            json.put(
                "days",
                JSONArray(
                    database.dayLogs().snapshot().mapNotNull { entity ->
                        val log = entity.toLog()
                        val filtered = log.journalSelections().mapValues { (section, tags) ->
                            when {
                                diary ||
                                    (
                                        ShareCategory.SECTIONS in selection.categories &&
                                            section in selection.sections
                                        ) -> tags

                                ShareCategory.TAGS in selection.categories -> tags.intersect(
                                    selection.tags[section].orEmpty()
                                )

                                else -> emptySet()
                            }
                        }.filterValues { it.isNotEmpty() }
                        val note = if (diary ||
                            (
                                ShareCategory.SECTIONS in selection.categories &&
                                    "note" in selection.sections
                                )
                        ) {
                            log.note
                        } else {
                            ""
                        }
                        if (filtered.isEmpty() &&
                            note.isEmpty()
                        ) {
                            null
                        } else {
                            JSONObject().put("day", entity.day)
                                .put(
                                    "sections",
                                    JSONObject().apply {
                                        filtered.forEach { (id, tags) ->
                                            put(id, JSONArray(tags.toList()))
                                        }
                                    }
                                ).put("note", note)
                        }
                    }
                )
            )
            // Share only names needed for selected data, never unselected sensitive tag names.
            val layout =
                database.journal().snapshot()?.let { JournalCodec.decode(it.config) }
                    ?: JournalLayout()
            json.put(
                "labels",
                JSONObject().apply {
                    layout.historySections().forEach { section ->
                        if (diary || section.id in selection.sections ||
                            section.id in selection.tags
                        ) {
                            put(
                                section.id,
                                JSONObject().put("title", section.title).put(
                                    "tags",
                                    JSONObject().apply {
                                        section.tags.filter {
                                            diary || section.id in selection.sections ||
                                                it.id in selection.tags[section.id].orEmpty()
                                        }
                                            .forEach { put(it.id, it.title) }
                                    }
                                )
                            )
                        }
                    }
                }
            )
        }
        json
    }
    suspend fun prepareData(
        identity: String,
        device: String,
        selection: ShareScope
    ): PartnerMessage {
        val normalized = app.ritela.domain.PartnerSharing.outgoing(selection)
        require(normalized.categories.isNotEmpty())
        return prepare(identity, device, "DATA", preview(normalized), normalized)
    }
    suspend fun prepareDoodle(identity: String, device: String, doodle: Doodle): PartnerMessage =
        prepare(identity, device, "DOODLE", PartnerCodec.doodle(doodle))

    /** Called only after the user confirms the recipient and preview for this exchange. */
    suspend fun confirmData(
        identity: String,
        device: String,
        selection: ShareScope,
        note: String = "",
        doodle: Doodle? = null
    ): PartnerMessage = prepare(
        identity,
        device,
        "DATA",
        JSONObject(),
        selection,
        confirmed = true,
        note = note,
        doodle = doodle
    )
    private suspend fun prepare(
        identity: String,
        device: String,
        kind: String,
        content: JSONObject,
        selection: ShareScope? = null,
        confirmed: Boolean = false,
        note: String = "",
        doodle: Doodle? = null,
        packetId: String? = null
    ): PartnerMessage = withContext(Dispatchers.IO) {
        run {
            database.withTransaction {
                val directory = snapshot()
                val contact = directory.identities.single { it.id == identity }
                check(contact.exchangeEnabled)
                val chosen = if (confirmed) {
                    val layout =
                        database.journal().snapshot()?.let { JournalCodec.decode(it.config) }
                            ?: JournalLayout()
                    app.ritela.domain.PartnerSharing.remembered(requireNotNull(selection), layout)
                        .also { require(it.categories.isNotEmpty()) }
                } else {
                    selection?.also { require(contact.grants.permits(it)) }
                }
                val payload = if (confirmed) {
                    preview(requireNotNull(chosen)).also { json ->
                        val text = app.ritela.domain.SavedDoodles.note(note)
                        if (text.isNotEmpty()) json.put("note", text)
                        doodle?.let { json.put("doodle", PartnerCodec.doodle(it)) }
                    }
                } else {
                    content
                }
                val remote = contact.devices.single {
                    it.id == device && it.enabled &&
                        it.mutualConfirmed
                }
                val keys = loadKeys()
                val id = packetId ?: UUID.randomUUID().toString()
                val created = now()
                val body = JSONObject().put("format", "ritela.partner").put("v", 1).put("id", id)
                    .put(
                        "from",
                        keys.identityId
                    ).put("device", keys.deviceId).put("to", identity).put("targetDevice", device)
                    .put(
                        "link",
                        remote.remoteNonce
                    ).put("kind", kind).put("created", created).put("content", payload).toString()
                val secret = PartnerCrypto.random(32)
                val iv = PartnerCrypto.random(12)
                val key = SecretKeySpec(secret, "AES")
                val wrapped = try {
                    PartnerCrypto.wrap(PartnerCrypto.publicKey(contact.publicKey), secret)
                } finally {
                    secret.fill(0)
                }
                val ciphertext = PartnerCrypto.crypt(
                    Cipher.ENCRYPT_MODE,
                    key,
                    iv,
                    "ritela.partner:1",
                    body.toByteArray()
                )
                val header = JSONObject().put("format", "ritela.partner.encrypted").put("v", 1)
                    .put("from", keys.identityId).put("device", keys.deviceId).put("to", identity)
                    .put("targetDevice", device).put("key", PartnerCrypto.b64(wrapped))
                    .put(
                        "iv",
                        PartnerCrypto.b64(iv)
                    ).put("data", PartnerCrypto.b64(ciphertext)).toString()
                val envelope = JSONObject().put("header", header).put(
                    "signature",
                    PartnerCrypto.sign(
                        PartnerCrypto.privateKey(keys.devicePrivate, "EC"),
                        header
                    )
                ).toString()
                require(envelope.toByteArray().size <= PartnerCrypto.MAX_PACKET)
                PartnerMessage(
                    id, identity, device, true, kind, created, created, payload.toString(), envelope
                ).also {
                    dao.insert(it)
                    rememberDoodle(it)
                    if (confirmed) {
                        dao.save(
                            PartnerDirectoryRow(
                                json = PartnerCodec.directory(
                                    directory.copy(
                                        identities = directory.identities.map { partner ->
                                            if (partner.id == identity) {
                                                partner.copy(
                                                    grants = requireNotNull(chosen),
                                                    lastSelection = chosen
                                                )
                                            } else {
                                                partner
                                            }
                                        }
                                    )
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun receive(bytes: ByteArray): ReceivedPartnerPacket = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(bytes.size in 1..PartnerCrypto.MAX_PACKET)
            val envelope = JSONObject(bytes.toString(Charsets.UTF_8))
            val headerRaw = envelope.getString("header")
            val header = JSONObject(headerRaw)
            require(
                header.getString("format") == "ritela.partner.encrypted" && header.getInt("v") == 1
            )
            val keys = loadKeys()
            require(
                header.getString("to") == keys.identityId &&
                    header.getString("targetDevice") == keys.deviceId
            )
            database.withTransaction {
                val sender = snapshot().identities.single {
                    it.id == header.getString("from") &&
                        it.exchangeEnabled
                }
                val device = sender.devices.single {
                    it.id == header.getString("device") &&
                        it.enabled && it.mutualConfirmed
                }
                require(
                    PartnerCrypto.verify(
                        PartnerCrypto.publicKey(device.publicKey, "EC"),
                        headerRaw,
                        envelope.getString("signature")
                    )
                )
                val secret = PartnerCrypto.unwrap(
                    PartnerCrypto.privateKey(keys.identityPrivate),
                    PartnerCrypto.bytes(header.getString("key"))
                )
                val decrypted = try {
                    PartnerCrypto.crypt(
                        Cipher.DECRYPT_MODE,
                        SecretKeySpec(secret, "AES"),
                        PartnerCrypto.bytes(header.getString("iv")),
                        "ritela.partner:1",
                        PartnerCrypto.bytes(header.getString("data"))
                    )
                } finally {
                    secret.fill(0)
                }
                val body = try {
                    JSONObject(decrypted.toString(Charsets.UTF_8))
                } finally {
                    decrypted.fill(0)
                }
                require(body.getString("format") == "ritela.partner" && body.getInt("v") == 1)
                listOf("from", "device", "to", "targetDevice").forEach {
                    require(body.getString(it) == header.getString(it))
                }
                require(body.getString("link") == device.localNonce)
                val id = body.getString("id").also { require(UUID.fromString(it).toString() == it) }
                val kind = body.getString("kind")
                require(kind in listOf("DATA", "DOODLE", "ACK", "OPENED", "SYNC_CAPS", "SYNC"))
                val content = body.getJSONObject("content")
                if (kind == "SYNC") {
                    require(bytes.size <= SyncLimits.ENVELOPE_BYTES)
                    requireSyncCapabilities(sender.id, device.id)
                    syncApply.receive(id, sender.id, device.id, bytes, content, now())
                }
                if (kind == "DOODLE") PartnerCodec.doodle(content)
                if (kind == "DATA") validateData(content)
                val previous = dao.message(id)
                if (previous != null) {
                    require(
                        !previous.outgoing && previous.identity == sender.id &&
                            previous.device == device.id
                    )
                    // A cleared message is a tombstone, never restored by replay.
                    require(
                        previous.envelope.isEmpty() ||
                            previous.envelope == bytes.toString(Charsets.UTF_8)
                    )
                    return@withTransaction ReceivedPartnerPacket(previous, true)
                }
                if (kind == "SYNC_CAPS") {
                    SyncEnvelopeCodec.validateCapabilities(content)
                    require(
                        content.getString("senderNonce") == device.remoteNonce &&
                            content.getString("recipientNonce") == device.localNonce
                    )
                    val context = "${device.localNonce}:${device.remoteNonce}"
                    val old = database.syncReceive().capability(sender.id, device.id)
                    database.syncReceive().capability(
                        SyncCapabilityRow(
                            sender.id,
                            device.id,
                            context,
                            old?.context == context && old.offered,
                            true
                        )
                    )
                }
                if (kind == "ACK" || kind == "OPENED") {
                    val original = dao.message(content.getString("message"))
                    require(
                        original != null && original.outgoing && original.identity == sender.id &&
                            original.device == device.id
                    )
                    if (kind == "ACK" && original.kind == "SYNC") {
                        val ids = content.getJSONArray("applied")
                        require(ids.length() <= SyncLimits.ACK_IDS)
                        for (i in 0 until ids.length()) {
                            val applied = requireNotNull(dao.message(ids.getString(i)))
                            require(
                                applied.outgoing && applied.kind == "SYNC" &&
                                    applied.identity == sender.id && applied.device == device.id
                            )
                            dao.delivered(applied.id)
                            database.syncQueue().cancel(applied.id)
                        }
                    } else if (kind == "ACK") {
                        dao.delivered(original.id)
                    } else {
                        require(
                            original.kind == "DOODLE" ||
                                (
                                    original.kind == "DATA" && original.body.isNotEmpty() &&
                                        JSONObject(original.body).has("doodle")
                                    ) ||
                                (
                                    original.kind == "DATA" && original.body.isEmpty() &&
                                        database.syncReceive().hasDoodle(original.id) == 1
                                    )
                        )
                        dao.delivered(original.id)
                        dao.opened(original.id)
                    }
                }
                val message =
                    PartnerMessage(
                        id, sender.id, device.id, false, kind,
                        body.getLong("created").also {
                            require(it in 0..253402300799999L)
                        },
                        now(),
                        content.toString(), bytes.toString(Charsets.UTF_8), delivered = true
                    )
                dao.insert(message)
                ReceivedPartnerPacket(message, false)
            }
        }
    }
    suspend fun acknowledgement(message: PartnerMessage): PartnerMessage {
        require(!message.outgoing)
        val content = JSONObject().put("message", message.id)
        if (message.kind == "SYNC") {
            val own = database.syncReceive().packet(message.id)
            val applied = database.syncReceive().appliedIds(message.identity, message.device)
            val ids = if (own?.applied == 1) {
                (listOf(message.id) + applied).distinct().take(SyncLimits.ACK_IDS)
            } else {
                applied
            }
            content.put(
                "applied",
                org.json.JSONArray(ids)
            )
        }
        return prepare(message.identity, message.device, "ACK", content)
    }
    private val openingMutex = Mutex()
    suspend fun openDoodle(id: String, reply: Boolean = true): PartnerMessage? =
        openingMutex.withLock {
            // A file reply is optional. Bluetooth still returns ACK on the same connection.
            run {
                val message = dao.message(id) ?: error("Unknown doodle")
                require(
                    message.kind in setOf("DATA", "DOODLE") &&
                        !message.outgoing && message.body.isNotEmpty()
                )
                val content = JSONObject(message.body)
                val doodle = if (message.kind ==
                    "DATA"
                ) {
                    content.getJSONObject("doodle")
                } else {
                    content
                }
                require(
                    PartnerCodec.doodle(doodle).canOpen(
                        message.received,
                        now()
                    )
                )
                val previousReceipt = dao.history(message.identity).firstOrNull {
                    it.outgoing && it.kind == "OPENED" && it.body.isNotEmpty() &&
                        JSONObject(it.body).optString("message") == id
                }
                if (message.opened && (previousReceipt != null || !reply)) return@run null
                val contact = snapshot().identities.firstOrNull { it.id == message.identity }
                val canReply = reply && contact?.exchangeEnabled == true && contact.devices.any {
                    it.id == message.device && it.enabled && it.mutualConfirmed
                }
                val receipt = if (canReply) {
                    previousReceipt ?: prepare(
                        message.identity,
                        message.device,
                        "OPENED",
                        JSONObject().put("message", id)
                    )
                } else {
                    null
                }
                dao.opened(id)
                receipt
            }
        }
    suspend fun retry(id: String): PartnerMessage {
        val message = requireNotNull(dao.message(id))
        require(message.outgoing && !message.hiddenFromChat && message.envelope.isNotEmpty())
        val keys = identity()
        val header = JSONObject(JSONObject(message.envelope).getString("header"))
        require(
            header.getString("from") == keys.identityId &&
                header.getString("device") == keys.deviceId
        )
        val directory = snapshot()
        val identity = directory.identities.single {
            it.id == message.identity && it.exchangeEnabled
        }
        require(
            identity.devices.any {
                it.id == message.device && it.enabled && it.mutualConfirmed
            }
        )
        if (message.kind == "DATA") {
            require(
                identity.grants.permits(
                    PartnerCodec.scope(JSONObject(message.body).getJSONObject("scope"))
                )
            )
        }
        if (message.kind == "SYNC") {
            requireSyncCapabilities(message.identity, message.device)
            val author = database.syncState().authorities().single()
            val content = JSONObject(message.body)
            check(
                author.state == "ACTIVE" && author.authorDevice == keys.deviceId &&
                    author.ownerIdentity == keys.identityId &&
                    author.generation == content.getLong("generation") &&
                    author.grantDigest == content.getString("grant")
            )
            require(identity.grants.permits(PartnerCodec.scope(content.getJSONObject("scope"))))
            database.syncState().links().singleOrNull {
                it.peerIdentity == message.identity && it.peerDevice == message.device
            }?.let { require(it.scopeEpoch == content.getLong("epoch")) }
        }
        return message
    }
    suspend fun exportIdentity(password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        try {
            val keys = loadKeys()
            val salt = PartnerCrypto.random(16)
            val iv = PartnerCrypto.random(12)
            val plain = JSONObject().put(
                "public",
                keys.identityPublic
            ).put("private", keys.identityPrivate).toString().toByteArray()
            val encrypted = try {
                PartnerCrypto.crypt(
                    Cipher.ENCRYPT_MODE,
                    PartnerCrypto.passwordKey(password, salt),
                    iv,
                    "ritela.identity:1",
                    plain
                )
            } finally {
                plain.fill(0)
            }
            JSONObject().put(
                "format",
                "ritela.identity"
            ).put("v", 1).put("salt", PartnerCrypto.b64(salt))
                .put(
                    "iv",
                    PartnerCrypto.b64(iv)
                ).put("data", PartnerCrypto.b64(encrypted)).toString().toByteArray()
        } finally {
            password.fill('\u0000')
        }
    }
    suspend fun restoreIdentity(bytes: ByteArray, password: CharArray) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    require(bytes.size <= 16000)
                    val json = JSONObject(bytes.toString(Charsets.UTF_8))
                    require(json.getString("format") == "ritela.identity" && json.getInt("v") == 1)
                    // Restoring requires a fresh profile to avoid orphaning live links.
                    require(snapshot().identities.isEmpty())
                    val plain = PartnerCrypto.crypt(
                        Cipher.DECRYPT_MODE,
                        PartnerCrypto.passwordKey(
                            password,
                            PartnerCrypto.bytes(json.getString("salt"))
                        ),
                        PartnerCrypto.bytes(json.getString("iv")),
                        "ritela.identity:1",
                        PartnerCrypto.bytes(json.getString("data"))
                    )
                    val restored = try {
                        JSONObject(plain.toString(Charsets.UTF_8))
                    } finally {
                        plain.fill(0)
                    }
                    val public = PartnerCrypto.publicKey(restored.getString("public"))
                    val private = PartnerCrypto.privateKey(restored.getString("private"))
                    require(
                        PartnerCrypto.verify(
                            public,
                            "restore",
                            PartnerCrypto.sign(private, "restore")
                        )
                    )
                    val replacement = LocalPartnerKeys.create(KeyPair(public, private))
                    // Persist the fence before changing the vault: a crash cannot bootstrap g=1
                    // from a restored identity. Manual v1 pairing/exchange remains available.
                    database.withTransaction {
                        database.syncState().ensureDefaults()
                        val authority = database.syncState().authorities().single()
                        require(authority.generation == 0L)
                        database.syncState().saveAuthority(
                            app.ritela.data.sync.SyncAuthorityRow(state = "RECOVERY_REQUIRED")
                        )
                    }
                    saveKeys(replacement)
                } finally {
                    password.fill('\u0000')
                }
            }
        }
    private fun validateData(content: JSONObject) {
        val scope = PartnerCodec.scope(content.getJSONObject("scope"))
        require(scope.categories.isNotEmpty())
        fun day(value: Long) {
            require(value in -25567L..376199L)
        }
        content.optJSONArray("periods")?.let { array ->
            require(
                (ShareCategory.PERIODS in scope.categories && array.length() <= 10000) ||
                    (ShareCategory.FORECASTS in scope.categories && array.length() <= 1)
            )
            PartnerCodec.objects(array).forEach {
                UUID.fromString(it.getString("id"))
                day(it.getLong("start"))
                if (!it.isNull("end")) {
                    day(it.getLong("end"))
                    require(it.getLong("end") >= it.getLong("start"))
                }
                require(it.getLong("updated") >= 0)
            }
        }
        content.optJSONArray("days")?.let { array ->
            require(
                scope.categories.any {
                    it in
                        listOf(ShareCategory.DIARY, ShareCategory.SECTIONS, ShareCategory.TAGS)
                }
            )
            require(array.length() <= 10000)
            PartnerCodec.objects(array).forEach { entry ->
                day(entry.getLong("day"))
                require(entry.getString("note").length <= 5000)
                val sections = entry.getJSONObject("sections")
                require(sections.length() <= 64)
                sections.keys().asSequence().forEach { id ->
                    require(validJournalId(id))
                    val tags = PartnerCodec.strings(sections.getJSONArray(id))
                    require(tags.size <= 64 && tags.all(::validJournalId))
                }
            }
        }
        content.optJSONArray("forecasts")?.let { array ->
            require(ShareCategory.FORECASTS in scope.categories && array.length() <= 366)
            PartnerCodec.objects(array).forEach {
                listOf("start", "lower", "upper", "ovulation").forEach { key ->
                    day(it.getLong(key))
                }
                require(it.getLong("start") in it.getLong("lower")..it.getLong("upper"))
            }
        }
        content.optJSONObject("settings")?.let {
            require(ShareCategory.SETTINGS in scope.categories)
            require(it.getString("language") in listOf("system", "ru", "en"))
            ThemeMode.valueOf(it.getString("theme"))
        }
        if (content.has("note")) {
            require(
                content.getString("note").length in 1..app.ritela.domain.SavedDoodles.NOTE_LIMIT
            )
        }
        if (content.has("doodle")) PartnerCodec.doodle(content.getJSONObject("doodle"))
        require(
            content.keys().asSequence().all {
                it in setOf(
                    "scope", "periods", "days", "labels", "forecasts",
                    "cycleDay", "duration", "settings", "phase", "asOf",
                    "calculatedAt", "algorithm", "cycleLength", "note", "doodle"
                )
            }
        )
        content.optJSONObject("labels")?.let { labels ->
            require(labels.length() <= 64)
            labels.keys().asSequence().forEach { id ->
                require(validJournalId(id))
                val section = labels.getJSONObject(id)
                require(section.getString("title").length <= 200)
                val tags = section.getJSONObject("tags")
                require(tags.length() <= 64)
                tags.keys().asSequence().forEach { tag ->
                    require(validJournalId(tag) && tags.getString(tag).length <= 200)
                }
            }
        }
        if (content.has("phase")) EstimatedCyclePhase.valueOf(content.getString("phase"))
        if (content.has("asOf")) day(content.getLong("asOf"))
        if (content.has("duration")) require(content.getInt("duration") in 1..60)
        if (content.has("calculatedAt")) require(content.getLong("calculatedAt") >= 0)
        if (content.has("algorithm")) require(content.getString("algorithm").length in 1..80)
        if (!content.isNull("cycleLength")) require(content.getInt("cycleLength") in 1..400000)
        if (!content.isNull("cycleDay")) require(content.getLong("cycleDay") in 1..400000)
    }
}
