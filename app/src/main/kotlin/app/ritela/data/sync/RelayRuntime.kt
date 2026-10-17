package app.ritela.data.sync

import android.content.Context
import androidx.room.withTransaction
import app.ritela.data.PartnerCrypto
import app.ritela.data.PartnerMessage
import app.ritela.data.PartnerRepository
import app.ritela.data.RitelaDatabase
import app.ritela.domain.sync.SyncBackoff
import app.ritela.domain.sync.SyncLease
import java.io.IOException
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

enum class RelayRun { IDLE, SUCCESS, MORE, RETRY, PAUSED }

/** One profile, one serialized pump. No key creation/network in constructor or opt-out reads. */
class RelayRuntime(
    context: Context,
    val profile: String,
    private val database: RitelaDatabase,
    private val partner: () -> PartnerRepository,
    private val current: () -> Boolean,
    private val origin: String,
    private val vault: RelayKeyVault = RelayKeyVault(context, profile),
    private val exchange: RelayHttpExchange = HttpsRelayExchange(),
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val jobs = ConcurrentHashMap.newKeySet<Job>()

    @Volatile private var closed = false
    private val dao get() = database.relay()
    val available get() = origin.isNotEmpty()
    internal var prepareAutomatic: (suspend () -> Unit)? = null
    internal var authorizeAutomatic: (suspend (String) -> Boolean)? = null
    internal var afterDelivery: (suspend () -> Boolean)? = null

    suspend fun registered(): Boolean = enabled() && dao.config()?.registered == true
    suspend fun sendRoute(peer: String, device: String): String? = if (!registered()) {
        null
    } else {
        dao.routes().lastOrNull {
            !it.incoming && it.identity == peer && it.device == device && valid(it)
        }?.route
    }

    /** Signed control data is sealed before enqueue; route capabilities never enter message.body. */
    suspend fun queueControl(
        peer: String,
        device: String,
        offer: JSONObject,
        extra: JSONObject = JSONObject()
    ): PartnerMessage = tracked {
        check(registered())
        val route = dao.routes().last {
            !it.incoming && it.identity == peer && it.device == device &&
                valid(it)
        }
        val cert = RelayTransportCertificate.verify(
            partner().snapshot().identities.single {
                it.id ==
                    peer
            },
            device,
            credentials(route).getString("certificate")
        )
        database.withTransaction {
            check(enabled())
            val (message, payload) = partner().prepareRelayControl(peer, device, offer, extra)
            val local = partner().identity()
            val remote = partner().snapshot().identities.single { it.id == peer }.devices.single {
                it.id ==
                    device
            }
            val bytes = PartnerEnvelopeCrypto.seal(
                local,
                peer,
                device,
                remote.remoteNonce,
                route.route,
                message.id,
                cert.getString("hpke"),
                JSONObject().put(
                    "kind",
                    message.kind
                ).put("created", message.created).put("payload", payload)
            )
            require(dao.bytes() + bytes.size <= 16 * 1024 * 1024 && dao.count() < 65536)
            dao.packet(
                RelayPacketRow(
                    message.id, peer, device, route.route,
                    minOf(
                        route.expires,
                        now() + 7 * 86400000L
                    ),
                    0, null, bytes, sourceDigest(message)
                )
            )
            check(SyncMessageQueue(database).offer(message) == SyncMessageOffer.QUEUED)
            message
        }
    }

    suspend fun receiveOffer(peer: String, device: String, certificate: String): String {
        check(registered())
        val existing = dao.routes().lastOrNull {
            it.incoming && it.identity == peer &&
                it.device == device &&
                valid(it) &&
                credentials(it).optString("authority").isEmpty() &&
                credentials(it).optString("certificate") == certificate &&
                credentials(it).has("offer")
        }
        return existing?.let { credentials(it).getString("offer") }
            ?: createReceiveRoute(peer, device, certificate)
    }

    suspend fun foregroundReady(): Boolean = enabled() && dao.config()?.let {
        it.nextAttempt <= now()
    } == true

    /** Explicit retry may reopen validation failures, but never bypasses server Retry-After. */
    suspend fun retry() {
        if (!enabled()) return
        val config = dao.config() ?: return
        if (config.nextAttempt ==
            Long.MAX_VALUE
        ) {
            dao.config(config.copy(nextAttempt = 0, error = null))
        }
    }

    /** A local routing decision for the explicit Send action; never starts network work. */
    suspend fun canDeliver(message: PartnerMessage): Boolean {
        if (!available || !enabled() || dao.config()?.registered != true) return false
        return authorizedSource(message.id) && linked(message.identity, message.device)
    }

    suspend fun linked(peer: String, device: String): Boolean {
        if (!available || !enabled() || dao.config()?.registered != true) return false
        return dao.routes().any {
            !it.incoming && it.identity == peer && it.device == device && valid(it)
        }
    }

    private suspend fun enabled(): Boolean = !closed && current() &&
        database.syncState().profileStates().singleOrNull()?.let {
            it.id == 1 &&
                it.internetEnabled == 1
        } ==
        true && dao.config()?.origin == origin && available

    suspend fun cancelRequests() {
        jobs.toList().forEach { it.cancelAndJoin() }
    }
    suspend fun close() {
        closed = true
        cancelRequests()
    }

    suspend fun activate(token: String = "", consent: Boolean) {
        require(
            consent && current() && available &&
                (
                    token.isEmpty() || token.matches(Regex("[0-9a-f]{64}")) ||
                        dao.config()?.registered == true
                    )
        )
        endpoint(origin)
        database.withTransaction {
            database.syncState().ensureDefaults()
            dao.consent(1)
            vault.create(true)
            val old = dao.config()
            require(old == null || old.origin == origin)
            if (old?.registered != true) {
                dao.config(
                    RelayConfigRow(
                        origin = origin,
                        activation = token.takeIf { it.isNotEmpty() }?.let {
                            vault.protect("activation", it.toByteArray())
                        }
                    )
                )
            } else {
                dao.config(old.copy(nextAttempt = 0, attempts = 0, error = null))
            }
        }
    }

    suspend fun certificate(): String {
        check(enabled() && dao.config()?.registered == true)
        return RelayTransportCertificate.issue(partner().identity(), requireNotNull(vault.load()))
    }

    /** Publish an already approved first-generation fence; never activates an author. */
    suspend fun registerAuthorFence(): String = tracked {
        check(enabled() && dao.config()?.registered == true)
        val local = partner().identity()
        val state = database.syncState().authorities().single()
        require(
            state.state == "ACTIVE" && state.generation == 1L &&
                state.ownerIdentity == local.identityId && state.authorDevice == local.deviceId
        )
        val keys = requireNotNull(vault.load())
        client(keys).request(
            "POST",
            "/v1/authorities/${keys.handle}/initialize",
            JSONObject().put("grant", requireNotNull(state.grantDigest)).toString().toByteArray()
        )
        keys.handle
    }

    suspend fun acceptSetup(peer: String, device: String, value: JSONObject) {
        check(enabled() && dao.config()?.registered == true)
        val cert = value.getString("certificate")
        RelayTransportCertificate.verify(
            partner().snapshot().identities.single {
                it.id == peer
            },
            device,
            cert
        )
        dao.peer(RelayPeerRow(peer, device, cert))
        if (value.has("route")) installSendRoute(value.getString("route"))
    }

    /** Two explicit manual exchanges: public certificates first, receiver-owned routes next. */
    suspend fun prepareSetup(peer: String, device: String): PartnerMessage {
        check(enabled() && dao.config()?.registered == true)
        val offer = JSONObject().put("certificate", certificate())
        val known = dao.peer(peer, device)
        if (known != null) offer.put("route", createReceiveRoute(peer, device, known.certificate))
        return partner().prepareRelaySetup(peer, device, offer)
    }

    /** Receiver creates a route; signed offer must be handed to the chosen peer explicitly. */
    suspend fun createReceiveRoute(
        peer: String,
        device: String,
        certificate: String,
        authority: String? = null,
        baselineBytes: Int = 0,
        baselineParts: Int = 0
    ): String = tracked {
        check(enabled() && dao.config()?.registered == true)
        val contact = partner().snapshot().identities.single { it.id == peer }
        val cert = RelayTransportCertificate.verify(contact, device, certificate)
        require(authority == null || authority == cert.getString("handle"))
        val keys = requireNotNull(vault.load())
        val route = PartnerCrypto.random(32).joinToString("") { "%02x".format(it) }
        val capability = PartnerCrypto.random(32).joinToString("") { "%02x".format(it) }
        val expiry = now() + 7 * 86400000L
        val reservationExpires = if (baselineBytes > 0) now() + 600000L else 0L
        val body = JSONObject().put("route", route).put("sender", cert.getString("handle"))
            .put("capability", capability).put("expiresAt", expiry).put("authority", authority)
        client(keys).request("POST", "/v1/routes", body.toString().toByteArray())
        val credentials = JSONObject().put(
            "capability",
            capability
        ).put("certificate", certificate).put(
            "authority",
            authority ?: ""
        )
        if (baselineBytes > 0) {
            require(authority != null && baselineBytes <= 6291456 && baselineParts in 1..64)
            val reservation = UUID.randomUUID().toString()
            client(keys).request(
                "POST",
                "/v1/routes/$route/reserve",
                JSONObject().put("id", reservation)
                    .put("bytes", baselineBytes).put("parts", baselineParts).put(
                        "expiresAt",
                        reservationExpires
                    )
                    .toString().toByteArray()
            )
            credentials.put("reservation", reservation)
        }
        database.withTransaction {
            check(enabled())
            dao.route(
                RelayRouteRow(
                    route,
                    peer,
                    device,
                    true,
                    expiry,
                    binding(peer, device),
                    vault.protect(route, credentials.toString().toByteArray())
                )
            )
        }
        val local = partner().identity()
        val raw = JSONObject().put("format", "ritela.route:1").put("owner", local.identityId)
            .put(
                "device",
                local.deviceId
            ).put("nonce", local.nonce).put("to", peer).put("target", device)
            .put("route", route).put("capability", capability).put("expires", expiry)
            .put("reservationExpires", reservationExpires)
            .put("authority", authority ?: "")
            .put(
                "certificate",
                certificate()
            ).put("reservation", credentials.optString("reservation", ""))
            .toString()
        JSONObject().put(
            "body",
            raw
        ).put(
            "signature",
            PartnerCrypto.sign(PartnerCrypto.privateKey(local.devicePrivate, "EC"), raw)
        ).toString().also { offer ->
            database.withTransaction {
                check(enabled())
                requireNotNull(dao.route(route))
                dao.routeCredentials(
                    route,
                    vault.protect(route, credentials.put("offer", offer).toString().toByteArray())
                )
            }
        }
    }

    suspend fun installSendRoute(offer: String) = database.withTransaction {
        check(enabled() && offer.toByteArray().size <= 16384)
        val envelope = JSONObject(offer)
        val raw = envelope.getString("body")
        val body = JSONObject(raw)
        val local = partner().identity()
        require(
            body.getString(
                "format"
            ) == "ritela.route:1" && body.getString("to") == local.identityId &&
                body.getString("target") == local.deviceId
        )
        val peer = body.getString("owner")
        val device = body.getString("device")
        val contact = partner().snapshot().identities.single { it.id == peer && it.exchangeEnabled }
        val remote = contact.devices.single { it.id == device && it.enabled && it.mutualConfirmed }
        require(
            body.getString(
                "nonce"
            ) == remote.remoteNonce && PartnerCrypto.verify(
                PartnerCrypto.publicKey(remote.publicKey, "EC"),
                raw,
                envelope.getString("signature")
            )
        )
        RelayTransportCertificate.verify(contact, device, body.getString("certificate"))
        val route = body.getString("route")
        require(
            route.matches(Regex("[0-9a-f]{64}")) &&
                body.getString("capability").matches(Regex("[0-9a-f]{64}"))
        )
        val expires = body.getLong("expires")
        require(expires > now() && expires <= now() + 7 * 86400000L)
        val old = dao.route(route)
        if (old ==
            null
        ) {
            dao.route(
                RelayRouteRow(
                    route,
                    peer,
                    device,
                    false,
                    expires,
                    binding(peer, device),
                    vault.protect(route, body.toString().toByteArray())
                )
            )
        } else {
            require(
                !old.incoming && old.identity == peer && old.device == device &&
                    old.expires == expires &&
                    JSONObject(
                        vault.unprotect(route, old.credentials).toString(Charsets.UTF_8)
                    ).toString() ==
                    body.toString()
            )
        }
        database.openHelper.writableDatabase.execSQL(
            "INSERT OR IGNORE INTO sync_links " +
                "(peerIdentity,peerDevice,protocolVersion,scopeEpoch) VALUES (?,?,2,1)",
            arrayOf(peer, device)
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE sync_links SET protocolVersion=2," +
                "scopeEpoch=MAX(scopeEpoch,1) WHERE peerIdentity=? AND peerDevice=?",
            arrayOf(peer, device)
        )
    }

    suspend fun runOnce(fetch: Boolean = true): RelayRun = tracked(serialize = false) {
        val result = mutex.withLock pump@{
            if (!enabled()) return@pump RelayRun.IDLE
            val config = dao.config() ?: return@pump RelayRun.IDLE
            if (config.origin != origin || !available) return@pump RelayRun.PAUSED
            if (config.nextAttempt == Long.MAX_VALUE) return@pump RelayRun.PAUSED
            if (config.nextAttempt > now()) return@pump RelayRun.RETRY
            try {
                val keys = requireNotNull(vault.load())
                val http = client(keys)
                if (!config.registered) {
                    val activation = config.activation?.let { vault.unprotect("activation", it) }
                    try {
                        val result = http.request(
                            "POST",
                            "/v1/devices",
                            JSONObject().put("handle", keys.handle)
                                .put(
                                    "publicKey",
                                    keys.apiPublic
                                ).apply {
                                    activation?.let {
                                        put("activation", it.toString(Charsets.UTF_8))
                                    }
                                }
                                .toString().toByteArray()
                        )
                        require(result.getString("handle") == keys.handle)
                        dao.config(config.copy(activation = null, registered = true))
                    } finally {
                        activation?.fill(0)
                    }
                }
                prepareAutomatic?.invoke()
                materialize()
                send(http)
                if (!fetch) {
                    dao.config(
                        requireNotNull(
                            dao.config()
                        ).copy(nextAttempt = 0, attempts = 0, error = null)
                    )
                    return@pump if (moreUploads()) RelayRun.MORE else RelayRun.SUCCESS
                }
                val page = http.fetch(requireNotNull(dao.config()).cursor)
                database.withTransaction {
                    check(enabled())
                    for (packet in page.packets) {
                        val route = requireNotNull(dao.route(packet.route))
                        require(route.incoming && valid(route))
                        val received = partner().receiveRelay(
                            keys.hpke,
                            route.identity,
                            route.route,
                            packet.id,
                            packet.ciphertext
                        )
                        require(received.message.device == route.device)
                        if (received.message.kind in
                            setOf("DATA", "DOODLE", "SYNC", "SYNC_CAPS")
                        ) {
                            partner().acknowledgement(received.message)
                        }
                    }
                }
                if (page.packets.isNotEmpty()) http.acknowledge(page.packets)
                database.withTransaction {
                    check(enabled())
                    dao.config(
                        requireNotNull(dao.config()).copy(
                            cursor = page.cursor,
                            nextAttempt = 0,
                            attempts = 0,
                            error = null,
                            lastCheck = now()
                        )
                    )
                }
                prepareAutomatic?.invoke()
                materialize()
                send(http)
                if (page.packets.isNotEmpty() || moreUploads()) {
                    RelayRun.MORE
                } else {
                    RelayRun.SUCCESS
                }
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (
                failure: RelayHttpFailure
            ) {
                failure(failure.retryable, "HTTP_${failure.status}", failure.retryAfterMillis)
            } catch (
                _: IOException
            ) {
                failure(true, "NETWORK")
            } catch (_: Exception) {
                failure(false, "VALIDATION")
            }
        }
        if (result !in setOf(RelayRun.SUCCESS, RelayRun.MORE)) return@tracked result
        try {
            if (afterDelivery?.invoke() == true) RelayRun.MORE else result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RelayHttpFailure) {
            mutex.withLock {
                failure(failure.retryable, "HTTP_${failure.status}", failure.retryAfterMillis)
            }
        } catch (_: IOException) {
            mutex.withLock { failure(true, "NETWORK") }
        } catch (_: Exception) {
            mutex.withLock { failure(false, "VALIDATION") }
        }
    }

    private fun reservationReady(route: RelayRouteRow, message: PartnerMessage): Boolean {
        if (message.kind != "SYNC" ||
            JSONObject(message.body).optString("kind") != "BASELINE"
        ) {
            return true
        }
        val credentials = credentials(route)
        return credentials.optString("reservation").isNotEmpty() &&
            credentials.optLong("reservationExpires", route.expires - 7 * 86400000L + 600000L) >
            now()
    }

    private suspend fun routeFor(message: PartnerMessage): RelayRouteRow? =
        dao.routes().lastOrNull {
            !it.incoming && it.identity == message.identity && it.device == message.device && valid(
                it
            ) &&
                (message.kind != "SYNC" || (authorRoute(it) && reservationReady(it, message)))
        }

    private fun authorRoute(route: RelayRouteRow): Boolean = credentials(route).let {
        it.optString("authority") == vault.load()?.handle ||
            (!it.has("authority") && it.optString("reservation").isNotEmpty())
    }

    internal suspend fun recordRouteReady(
        peer: String,
        device: String,
        baseline: Boolean
    ): Boolean = registered() &&
        dao.routes().any {
            !it.incoming && it.identity == peer && it.device == device && valid(it) &&
                authorRoute(it) &&
                (
                    !baseline ||
                        credentials(it).let { data ->
                            data.optString("reservation").isNotEmpty() &&
                                data.optLong(
                                    "reservationExpires",
                                    it.expires - 7 * 86400000L + 600000L
                                ) >
                                now()
                        }
                    )
        }

    private suspend fun materialize() {
        val messages = SyncMessageQueue(database).pending().map { it.id }.toSet()
        val ids = (messages + dao.pendingIds()).filter { dao.packet(it) == null }
        var wrapped = 0
        for (id in ids) {
            if (wrapped == 32) break
            if (dao.packet(id) != null) continue
            val message = database.partner().message(id) ?: continue
            val route = routeFor(message) ?: continue
            database.withTransaction {
                if (!authorizedSource(id)) return@withTransaction
                val content = JSONObject(message.body)
                val generation = if (message.kind == "SYNC") content.getLong("generation") else 0
                val grant = if (generation > 0) content.getString("grant") else null
                if (generation > 0) {
                    // Successor activation needs the explicit server transfer protocol.
                    require(generation == 1L)
                }
                if (!hasRoom()) return@withTransaction
                val credentials = credentials(route)
                val contact = partner().snapshot().identities.single { it.id == route.identity }
                val cert = RelayTransportCertificate.verify(
                    contact,
                    route.device,
                    credentials.getString("certificate")
                )
                val bytes = partner().sealRelay(id, route.route, cert.getString("hpke"))
                require(dao.bytes() + bytes.size <= 16 * 1024 * 1024)
                dao.packet(
                    RelayPacketRow(
                        id, message.identity, message.device, route.route,
                        minOf(
                            route.expires,
                            now() + 7 * 86400000L
                        ),
                        generation, grant, bytes, sourceDigest(message)
                    )
                )
                wrapped++
            }
        }
    }

    private suspend fun hasRoom() = dao.bytes() <= 16 * 1024 * 1024 - 256 * 1024 &&
        dao.count() < 65536

    private suspend fun moreUploads(): Boolean {
        if (dao.active().any { it.state == "PENDING" }) return true
        if (!hasRoom()) return false
        return dao.pendingIds().any { id ->
            dao.packet(id) == null && database.partner().message(id)?.let {
                routeFor(it) != null && authorizedSource(id)
            } == true
        }
    }

    private suspend fun send(http: HttpRelayTransport) {
        var uploads = 0
        for (packet in dao.active()) {
            if (database.partner().message(packet.id)?.delivered == true) {
                dao.retire(packet.id, "ACKED")
                continue
            }
            if (packet.expires <= now()) {
                database.syncMessageIntents().get(packet.id)?.let {
                    database.syncMessageIntents().finish(packet.id, "CANCELLED", "EXPIRED")
                }
                dao.retire(packet.id, "EXPIRED")
                database.syncQueue().packet(packet.id)?.let {
                    database.syncQueue().cancel(it.packetId)
                    database.syncQueue().invalidate(it.peerIdentity, it.peerDevice, it.scopeEpoch)
                }
                continue
            }
            if (!authorized(packet)) {
                if (enabled()) {
                    http.request(
                        "DELETE",
                        "/v1/routes/${packet.route}/packets/${packet.id}"
                    )
                }
                dao.retire(packet.id, "CANCELLED")
                continue
            }
            if (packet.nextAttempt > now()) continue
            if (uploads++ >= 16) continue
            if (packet.generation == 1L) {
                http.request(
                    "POST",
                    "/v1/authorities/${requireNotNull(vault.load()).handle}/initialize",
                    JSONObject().put("grant", packet.grant).toString().toByteArray()
                )
            }
            http.store(
                SyncLease(packet.id, "relay", packet.identity, packet.device, packet.envelope)
            )
            database.withTransaction {
                dao.state(packet.id, "STORED", Long.MAX_VALUE)
                database.syncQueue().packet(packet.id)?.let { dao.stored(packet.id) }
            }
        }
    }

    private suspend fun authorizedSource(id: String, upload: Boolean = true): Boolean {
        if (!enabled()) return false
        val message = database.partner().message(id) ?: return false
        if (message.delivered) return false
        return if (message.kind == "SYNC") {
            if (authorizeAutomatic?.invoke(id) == false) return false
            if (upload && routeFor(message) == null) return false
            val row = database.syncQueue().packet(id) ?: return false
            row.state != "CANCELLED" &&
                SyncQueueRepository(database, SyncPacketEncoder { error("ENCODER_UNUSED") })
                    .dispatchable(id) && runCatching { partner().retry(id) }.isSuccess
        } else {
            SyncMessageQueue(database).pending().any { it.id == id } &&
                runCatching { partner().retry(id) }.isSuccess
        }
    }

    private suspend fun authorized(packet: RelayPacketRow): Boolean {
        val message = database.partner().message(packet.id) ?: return false
        val route = dao.route(packet.route) ?: return false
        return valid(route) && sourceDigest(message) == packet.sourceDigest &&
            authorizedSource(packet.id, upload = packet.state != "STORED") &&
            (packet.state == "STORED" || reservationReady(route, message))
    }
    private suspend fun valid(route: RelayRouteRow): Boolean = route.expires > now() &&
        runCatching { route.context == binding(route.identity, route.device) }.getOrDefault(false)
    private suspend fun binding(peer: String, device: String): String {
        val contact = partner().snapshot().identities.single { it.id == peer && it.exchangeEnabled }
        val remote = contact.devices.single { it.id == device && it.enabled && it.mutualConfirmed }
        return SyncEnvelopeCodec.digest(
            listOf(
                remote.localNonce,
                remote.remoteNonce,
                remote.publicKey,
                remote.verificationContext
            ).joinToString(":").toByteArray()
        )
    }
    private fun credentials(route: RelayRouteRow) =
        JSONObject(vault.unprotect(route.route, route.credentials).toString(Charsets.UTF_8))
    private fun endpoint(value: String): RelayEndpoint {
        require(available && value == origin)
        return RelayEndpoint(value, setOf(URI(origin).host))
    }
    private fun client(keys: RelayKeys) = HttpRelayTransport(endpoint(origin), keys, { lease ->
        enabled() &&
            (
                lease == null ||
                    dao.packet(lease.packetId)?.let {
                        it.envelope.contentEquals(lease.bytes) &&
                            authorized(it)
                    } ==
                    true
                )
    }, { lease ->
        val packet = requireNotNull(dao.packet(lease.packetId))
        val secret = credentials(requireNotNull(dao.route(packet.route)))
        RelayUpload(
            packet.route,
            secret.getString("capability"),
            packet.expires,
            packet.generation,
            packet.grant,
            secret.optString("reservation", "").takeIf {
                it.isNotEmpty() &&
                    packet.generation > 0 && JSONObject(
                        requireNotNull(database.partner().message(packet.id)).body
                    ).optString("kind") == "BASELINE"
            }
        )
    }, exchange, now)
    private fun sourceDigest(message: PartnerMessage) =
        SyncEnvelopeCodec.digest(message.envelope.toByteArray())
    private suspend fun failure(retry: Boolean, code: String, delay: Long = 0): RelayRun {
        val config = requireNotNull(dao.config())
        val attempt = config.attempts.coerceIn(0, 99999) + 1
        dao.config(
            config.copy(
                attempts = attempt,
                error = code,
                nextAttempt = if (retry) {
                    now() + maxOf(
                        delay,
                        SyncBackoff.delay(attempt, 1.0)
                    )
                } else {
                    Long.MAX_VALUE
                }
            )
        )
        return if (retry) RelayRun.RETRY else RelayRun.PAUSED
    }
    private suspend fun <T> tracked(serialize: Boolean = true, action: suspend () -> T): T =
        coroutineScope {
            val job = async(start = CoroutineStart.LAZY) {
                if (serialize) {
                    mutex.withLock {
                        check(!closed)
                        action()
                    }
                } else {
                    check(!closed)
                    action()
                }
            }
            jobs.add(job)
            try {
                job.await()
            } finally {
                jobs.remove(job)
            }
        }
}
