package app.ritela.data

import androidx.room.withTransaction
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
import app.ritela.domain.journalSelections
import app.ritela.domain.validJournalId
import java.security.KeyPair
import java.time.LocalDate
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
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
    val device: PartnerDevice
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
    val messages = dao.messages()
    suspend fun identity(): LocalPartnerKeys = withContext(Dispatchers.IO) { loadKeys() }
    suspend fun snapshot(): PartnerDirectory =
        dao.snapshot()?.let { PartnerCodec.directory(it.json) } ?: PartnerDirectory()
    private suspend fun mutate(change: (PartnerDirectory) -> PartnerDirectory) = mutex.withLock {
        database.withTransaction {
            dao.save(PartnerDirectoryRow(json = PartnerCodec.directory(change(snapshot()))))
        }
    }
    suspend fun invitation(name: String): String = identity().qr(name)
    suspend fun inspectInvitation(qr: String): PairInvitation = withContext(Dispatchers.IO) {
        require(qr.length <= 12000)
        val envelope = JSONObject(qr)
        val raw = envelope.getString("body")
        val body = JSONObject(raw)
        require(body.getString("format") == "ritela.pair" && body.getInt("v") == 1)
        val key = body.getString("identity")
        val id = PartnerCrypto.id(key)
        val deviceId = body.getString("device").also {
            require(UUID.fromString(it).toString() == it)
        }
        val deviceKey = body.getString("key")
        val certificate = body.getString("certificate")
        val nonce = body.getString("nonce").also { require(UUID.fromString(it).toString() == it) }
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
            PartnerDevice(deviceId, deviceKey, certificate, identity().nonce, nonce)
        )
    }

    /** Call only after fingerprint comparison and explicit mutual-verification confirmation. */
    suspend fun pair(invitation: PairInvitation) {
        require(PartnerCrypto.id(invitation.key) == invitation.identity)
        require(
            PartnerCrypto.verify(
                PartnerCrypto.publicKey(invitation.key),
                "${invitation.identity}:${invitation.device.id}:${invitation.device.publicKey}",
                invitation.device.certificate
            )
        )
        val nonce = identity().nonce
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
                    listOf(invitation.device.copy(localNonce = nonce))
                )
            )
        }
    }

    suspend fun grant(identity: String, scope: ShareScope) = mutate { directory ->
        directory.copy(
            identities = directory.identities.map {
                if (it.id == identity) it.copy(grants = scope) else it
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
    suspend fun enableDevice(identity: String, device: String, value: Boolean) =
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
                                    d.copy(enabled = value)
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
        database.withTransaction { dao.clearReceived(identity) }
    }

    suspend fun preview(selection: ShareScope): JSONObject = database.withTransaction {
        val json = JSONObject().put("scope", PartnerCodec.scope(selection))
        val periods = database.periods().snapshot()
        if (ShareCategory.PERIODS in
            selection.categories
        ) {
            json.put(
                "periods",
                JSONArray(
                    periods.map {
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
                    layout.sections.forEach { section ->
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
        if (ShareCategory.SETTINGS in selection.categories) {
            val settings = database.profileSettings().snapshot() ?: ProfileSettings()
            json.put(
                "settings",
                JSONObject().put("language", settings.language).put("theme", settings.theme)
            )
        }
        json
    }
    suspend fun prepareData(
        identity: String,
        device: String,
        selection: ShareScope
    ): PartnerMessage {
        require(selection.categories.isNotEmpty())
        return prepare(identity, device, "DATA", preview(selection), selection)
    }
    suspend fun prepareDoodle(identity: String, device: String, doodle: Doodle): PartnerMessage =
        prepare(identity, device, "DOODLE", PartnerCodec.doodle(doodle))
    private suspend fun prepare(
        identity: String,
        device: String,
        kind: String,
        content: JSONObject,
        selection: ShareScope? = null
    ): PartnerMessage = withContext(Dispatchers.IO) {
        mutex.withLock {
            database.withTransaction {
                val contact = snapshot().identities.single { it.id == identity }
                check(contact.exchangeEnabled)
                selection?.let { require(contact.grants.permits(it)) }
                val remote = contact.devices.single { it.id == device && it.enabled }
                val keys = loadKeys()
                val id = UUID.randomUUID().toString()
                val created = now()
                val body = JSONObject().put("format", "ritela.partner").put("v", 1).put("id", id)
                    .put(
                        "from",
                        keys.identityId
                    ).put("device", keys.deviceId).put("to", identity).put("targetDevice", device)
                    .put(
                        "link",
                        remote.remoteNonce
                    ).put("kind", kind).put("created", created).put("content", content).toString()
                val secret = PartnerCrypto.random(32)
                val iv = PartnerCrypto.random(12)
                val ciphertext = try {
                    PartnerCrypto.crypt(
                        Cipher.ENCRYPT_MODE,
                        SecretKeySpec(secret, "AES"),
                        iv,
                        "ritela.partner:1",
                        body.toByteArray()
                    )
                } finally { /* cleared after RSA wrapping below */ }
                val header = JSONObject().put(
                    "format",
                    "ritela.partner.encrypted"
                ).put("v", 1).put("from", keys.identityId)
                    .put("device", keys.deviceId).put("to", identity).put("targetDevice", device)
                    .put(
                        "key",
                        PartnerCrypto.b64(
                            PartnerCrypto.wrap(PartnerCrypto.publicKey(contact.publicKey), secret)
                        )
                    )
                    .put(
                        "iv",
                        PartnerCrypto.b64(iv)
                    ).put("data", PartnerCrypto.b64(ciphertext)).toString()
                secret.fill(0)
                val envelope = JSONObject().put("header", header).put(
                    "signature",
                    PartnerCrypto.sign(
                        PartnerCrypto.privateKey(keys.devicePrivate, "EC"),
                        header
                    )
                ).toString()
                require(envelope.toByteArray().size <= PartnerCrypto.MAX_PACKET)
                PartnerMessage(
                    id, identity, device, true, kind, created, created, content.toString(), envelope
                ).also {
                    dao.insert(it)
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
                        it.enabled
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
                require(kind in listOf("DATA", "DOODLE", "ACK", "OPENED"))
                val content = body.getJSONObject("content")
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
                if (kind == "ACK" || kind == "OPENED") {
                    val original = dao.message(content.getString("message"))
                    require(
                        original != null && original.outgoing && original.identity == sender.id &&
                            original.device == device.id
                    )
                    if (kind == "ACK") {
                        dao.delivered(original.id)
                    } else {
                        require(original.kind == "DOODLE")
                        dao.delivered(original.id)
                        dao.opened(original.id)
                    }
                }
                val message =
                    PartnerMessage(
                        id, sender.id, device.id, false, kind, body.getLong("created"), now(),
                        content.toString(), bytes.toString(Charsets.UTF_8), delivered = true
                    )
                dao.insert(message)
                ReceivedPartnerPacket(message, false)
            }
        }
    }
    suspend fun acknowledgement(message: PartnerMessage): PartnerMessage =
        prepare(message.identity, message.device, "ACK", JSONObject().put("message", message.id))
    private val openingMutex = Mutex()
    suspend fun openDoodle(id: String): PartnerMessage? = openingMutex.withLock {
        // prepare persists the receipt before marking opened; recovery reuses that receipt.
        run {
            val message = dao.message(id) ?: error("Unknown doodle")
            require(message.kind == "DOODLE" && !message.outgoing && message.body.isNotEmpty())
            require(PartnerCodec.doodle(JSONObject(message.body)).canOpen(message.received, now()))
            if (message.opened) return@run null
            val receipt = dao.history(message.identity).firstOrNull {
                it.outgoing && it.kind == "OPENED" && it.body.isNotEmpty() &&
                    JSONObject(it.body).optString("message") == id
            }
                ?: prepare(
                    message.identity,
                    message.device,
                    "OPENED",
                    JSONObject().put("message", id)
                )
            dao.opened(id)
            receipt
        }
    }
    suspend fun retry(id: String): PartnerMessage {
        val message = requireNotNull(dao.message(id))
        require(message.outgoing && message.envelope.isNotEmpty())
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
        require(identity.devices.any { it.id == message.device && it.enabled })
        if (message.kind == "DATA") {
            require(
                identity.grants.permits(
                    PartnerCodec.scope(JSONObject(message.body).getJSONObject("scope"))
                )
            )
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
                    saveKeys(LocalPartnerKeys.create(KeyPair(public, private)))
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
            require(ShareCategory.PERIODS in scope.categories && array.length() <= 10000)
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
            require(ShareCategory.FORECASTS in scope.categories && array.length() <= 16)
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
        require(
            content.keys().asSequence().all {
                it in setOf(
                    "scope", "periods", "days", "labels", "forecasts",
                    "cycleDay", "duration", "settings", "phase", "asOf"
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
        if (!content.isNull("cycleDay")) require(content.getLong("cycleDay") in 1..400000)
    }
}
