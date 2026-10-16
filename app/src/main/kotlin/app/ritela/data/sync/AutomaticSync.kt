package app.ritela.data.sync

import androidx.room.withTransaction
import app.ritela.data.JournalCodec
import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerMessage
import app.ritela.data.PartnerRepository
import app.ritela.data.RitelaDatabase
import app.ritela.domain.JournalLayout
import app.ritela.domain.PartnerSharing
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Permissions and approvals are authenticated control messages, retained outside chat deletion. */
class AutomaticSync(
    private val database: RitelaDatabase,
    private val partner: PartnerRepository,
    private val runtime: RelayRuntime
) {
    private val controlsMutex = Mutex()
    val links = database.invalidationTracker.createFlow("sync_links").map {
        database.syncState().links()
    }.flowOn(Dispatchers.IO)
    suspend fun isApproved(messageId: String): Boolean {
        val message = database.partner().message(messageId) ?: return false
        return database.partner().controls(message.identity, message.device, true).any {
            val approval = JSONObject(it.body).optJSONObject("authorApproval")
            approval != null && approval.optString("request") == messageId &&
                approval.optBoolean("published")
        }
    }
    private fun queue() = SyncQueueRepository(
        database,
        SyncPacketEncoder(partner::encodeSyncDraft),
        ShareProjector { partner.preview(ShareScope(setOf(ShareCategory.FORECASTS))) }
    )

    suspend fun enable(peer: String, device: String, selection: ShareScope) =
        controlsMutex.withLock {
            enableInternal(peer, device, selection)
        }

    private suspend fun enableInternal(peer: String, device: String, selection: ShareScope) {
        check(runtime.linked(peer, device))
        val grant = partner.initializeSyncAuthor()
        runtime.registerAuthorFence()
        val scope = database.withTransaction {
            val layout =
                database.journal().snapshot()?.let { JournalCodec.decode(it.config) }
                    ?: JournalLayout()
            PartnerSharing.remembered(selection, layout).also {
                require(it.categories.isNotEmpty())
            }
        }
        val contact = partner.snapshot().identities.single { it.id == peer }
        partner.grant(peer, PartnerSharing.union(contact.grants, scope))
        val request = database.withTransaction {
            val old = database.syncState().links().single {
                it.peerIdentity == peer &&
                    it.peerDevice == device
            }
            if (old.automaticEnabled == 1 && old.scopeJson == PartnerCodec.scope(
                    scope
                ).toString() &&
                database.partner().controls(peer, device, true).any { message ->
                    JSONObject(message.body).optJSONObject("authorRequest")?.optLong("epoch") ==
                        old.scopeEpoch &&
                        JSONObject(
                            message.body
                        ).getJSONObject("authorRequest").optString("context") ==
                        context(peer, device) &&
                        (
                            database.syncMessageIntents().get(
                                message.id
                            )?.state == "PENDING_WRAP" ||
                                approved(peer, device, old.scopeEpoch)
                            )
                }
            ) {
                return@withTransaction null
            }
            check(
                partner.snapshot().identities.single {
                    it.id == peer && it.exchangeEnabled
                }.grants.permits(scope)
            )
            val epoch = Math.addExact(old.scopeEpoch, 1)
            database.openHelper.writableDatabase.execSQL(
                "UPDATE sync_links SET automaticEnabled=1,scopeEpoch=?,scopeJson=? WHERE peerIdentity=? AND peerDevice=?",
                arrayOf<Any>(epoch, PartnerCodec.scope(scope).toString(), peer, device)
            )
            check(queue().prepare(peer, device) == SyncPreparation.ENQUEUED)
            val packets = database.syncQueue().packets().filter {
                it.peerIdentity == peer &&
                    it.peerDevice == device &&
                    it.scopeEpoch == epoch
            }
            val bytes = packets.sumOf { it.envelope.size.toLong() * 2 + 2048L }
            require(bytes in 1..6291456L && packets.size in 1..64)
            JSONObject().put(
                "grant",
                grant
            ).put("scope", PartnerCodec.scope(scope)).put("epoch", epoch)
                .put(
                    "context",
                    context(peer, device)
                ).put("bytes", bytes).put("parts", packets.size)
        } ?: return
        runtime.queueControl(
            peer,
            device,
            JSONObject().put("certificate", runtime.certificate()),
            JSONObject().put("authorRequest", request)
        )
    }

    /** Recipient explicitly approves the displayed selection and author once, not on each update. */
    suspend fun approve(messageId: String) = controlsMutex.withLock { approveInternal(messageId) }

    private suspend fun approveInternal(messageId: String) {
        check(runtime.registered())
        val message = requireNotNull(database.partner().message(messageId))
        require(!message.outgoing && message.kind == "SYNC_CAPS")
        val request = JSONObject(message.body).getJSONObject("authorRequest")
        if (database.partner().controls(message.identity, message.device, true).any {
                val approval = JSONObject(it.body).optJSONObject("authorApproval")
                approval != null && approval.optString("request") == messageId &&
                    approval.optBoolean("published")
            }
        ) {
            return
        }
        require(request.getString("context") == context(message.identity, message.device))
        val scope = PartnerCodec.scope(request.getJSONObject("scope"))
        require(
            scope == scope.normalized() && scope.categories.isNotEmpty() &&
                scope.sections.all { it == "note" }
        )
        require(
            ShareCategory.DIARY !in scope.categories && ShareCategory.SETTINGS !in scope.categories
        )
        val certificate = requireNotNull(
            database.relay().peer(message.identity, message.device)
        ).certificate
        val contact = partner.snapshot().identities.single { it.id == message.identity }
        val handle = RelayTransportCertificate.verify(
            contact,
            message.device,
            certificate
        ).getString("handle")
        val grant = request.getString("grant")
        require(request.getInt("bytes") in 1..6291456 && request.getInt("parts") in 1..64)
        val epoch = SyncEnvelopeCodec.counter(request, "epoch")
        val body = JSONObject(JSONObject(grant).getString("body"))
        require(body.getLong("generation") == 1L)
        val approval = JSONObject().put("request", messageId)
            .put(
                "grant",
                SyncEnvelopeCodec.digest(grant.toByteArray())
            ).put("scope", PartnerCodec.scope(scope))
            .put(
                "epoch",
                epoch
            ).put("context", context(message.identity, message.device)).put("published", false)
        database.withTransaction {
            partner.prepareRelayControl(
                message.identity,
                message.device,
                JSONObject().put("certificate", runtime.certificate()),
                JSONObject().put("authorApproval", approval)
            )
            partner.approveSyncAuthor(message.identity, grant)
        }
        val route = runtime.createReceiveRoute(
            message.identity,
            message.device,
            certificate,
            handle,
            request.getInt("bytes"),
            request.getInt("parts")
        )
        runtime.queueControl(
            message.identity,
            message.device,
            JSONObject().put("certificate", runtime.certificate()).put("route", route),
            JSONObject().put("authorApproval", approval.put("published", true))
        )
    }

    suspend fun pause(peer: String, device: String) = database.withTransaction {
        database.syncSettings().pauseAutomatic(peer, device)
        database.syncSettings().invalidateRecords(peer, device)
        database.syncSettings().cancelRecords(peer, device)
    }

    suspend fun prepare() {
        if (!runtime.registered()) return
        for (link in database.syncState().links().filter {
            it.automaticEnabled == 1 &&
                it.scopeJson != null
        }) {
            if (!approved(link.peerIdentity, link.peerDevice, link.scopeEpoch)) continue
            if (!runtime.linked(link.peerIdentity, link.peerDevice)) continue
            refreshForecast(link)
            queue().prepare(link.peerIdentity, link.peerDevice)
        }
    }

    suspend fun authorized(id: String): Boolean {
        val packet = database.syncQueue().packet(id) ?: return false
        val requests = database.partner().controls(packet.peerIdentity, packet.peerDevice, true)
            .any { JSONObject(it.body).has("authorRequest") }
        return !requests || approved(packet.peerIdentity, packet.peerDevice, packet.scopeEpoch)
    }

    private suspend fun approved(peer: String, device: String, epoch: Long): Boolean {
        val binding = try {
            context(peer, device)
        } catch (
            _: NoSuchElementException
        ) {
            return false
        }
        val link =
            database.syncState().links().singleOrNull {
                it.peerIdentity == peer &&
                    it.peerDevice == device
            }
                ?: return false
        if (link.automaticEnabled != 1 || link.scopeEpoch != epoch ||
            link.scopeJson == null
        ) {
            return false
        }
        val grant = database.syncState().authorities().single().grantDigest
        return database.partner().controls(peer, device, false).any { message ->
            val approval =
                JSONObject(message.body).optJSONObject("authorApproval") ?: return@any false
            val request =
                database.partner().message(approval.getString("request")) ?: return@any false
            request.outgoing && request.identity == peer && request.device == device &&
                database.syncMessageIntents().get(request.id)?.state != "CANCELLED" &&
                approval.optBoolean("published") &&
                approval.getLong("epoch") == epoch && approval.getString("grant") == grant &&
                approval.getString("context") == binding &&
                PartnerCodec.scope(approval.getJSONObject("scope")) ==
                PartnerCodec.scope(JSONObject(link.scopeJson))
        }
    }

    suspend fun verifyIncoming(peer: String, device: String, content: JSONObject) {
        val approvals = database.partner().controls(peer, device, true).mapNotNull {
            JSONObject(it.body).optJSONObject("authorApproval")
        }
        if (approvals.isEmpty()) return
        val approval = approvals.firstOrNull { it.optString("context") == context(peer, device) }
        require(
            approval != null && approval.getLong("epoch") == content.getLong("epoch") &&
                approval.getString("grant") == content.getString("grant") &&
                PartnerCodec.scope(
                    approval.getJSONObject("scope")
                ).permits(PartnerCodec.scope(content.getJSONObject("scope")))
        )
    }

    /** Runs after the serialized pump, while its parent remains tracked for profile cancellation. */
    suspend fun refreshReservations(): Boolean = controlsMutex.withLock {
        if (!runtime.registered()) return@withLock false
        var progress = false
        for (peer in partner.snapshot().identities.filter { it.exchangeEnabled }) {
            for (device in peer.devices.filter { it.enabled && it.mutualConfirmed }) {
                // A failed or delayed publication can finish only under a persisted, matching consent.
                val incoming = database.partner().controls(peer.id, device.id, false)
                for (message in incoming.filter {
                    JSONObject(it.body).has("authorRequest")
                }.take(4)) {
                    if (isApproved(message.id)) continue
                    val request = JSONObject(message.body).getJSONObject("authorRequest")
                    val grant = SyncEnvelopeCodec.digest(request.getString("grant").toByteArray())
                    val allowed = database.partner().controls(peer.id, device.id, true).any { old ->
                        val approval =
                            JSONObject(old.body).optJSONObject("authorApproval") ?: return@any false
                        approval.getString("context") == context(peer.id, device.id) &&
                            approval.getString("grant") == grant &&
                            approval.getLong("epoch") == request.getLong("epoch") &&
                            PartnerCodec.scope(approval.getJSONObject("scope")) ==
                            PartnerCodec.scope(request.getJSONObject("scope"))
                    }
                    if (allowed) {
                        approveInternal(message.id)
                        progress = true
                        break
                    }
                }
            }
        }
        val authority = database.syncState().authorities().singleOrNull()
        if (authority?.state != "ACTIVE" || authority.generation != 1L) return@withLock progress
        for (link in database.syncState().links().filter {
            it.automaticEnabled == 1 &&
                it.scopeJson != null
        }) {
            val peer = link.peerIdentity
            val device = link.peerDevice
            if (!approved(peer, device, link.scopeEpoch) || !runtime.linked(peer, device)) continue
            val pending = database.syncQueue().activePackets().filter {
                it.peerIdentity == peer && it.peerDevice == device &&
                    it.scopeEpoch == link.scopeEpoch
            }
            if (pending.isEmpty() ||
                runtime.recordRouteReady(peer, device, pending.any { it.kind == "BASELINE" })
            ) {
                continue
            }
            val outgoing = database.partner().controls(peer, device, true).firstOrNull {
                val request = JSONObject(it.body).optJSONObject("authorRequest")
                request != null && request.optLong("epoch") == link.scopeEpoch &&
                    request.optString("context") == context(peer, device)
            }
            if (outgoing != null &&
                database.syncMessageIntents().get(outgoing.id)?.state != "CANCELLED" &&
                database.partner().controls(peer, device, false).none {
                    JSONObject(it.body).optJSONObject("authorApproval")?.optString("request") ==
                        outgoing.id
                }
            ) {
                continue
            }
            val request = database.withTransaction {
                if (database.syncState().links().none {
                        it.peerIdentity == peer && it.peerDevice == device &&
                            it.automaticEnabled == 1 &&
                            it.scopeEpoch == link.scopeEpoch
                    }
                ) {
                    return@withTransaction null
                }
                for (packet in database.syncQueue().packets().filter {
                    it.peerIdentity == peer && it.peerDevice == device &&
                        it.scopeEpoch == link.scopeEpoch &&
                        it.state != "CANCELLED"
                }) {
                    if (database.partner().message(packet.packetId)?.delivered !=
                        true
                    ) {
                        database.syncQueue().cancel(packet.packetId)
                    }
                }
                database.syncQueue().invalidate(peer, device, link.scopeEpoch)
                if (queue().prepare(peer, device) !=
                    SyncPreparation.ENQUEUED
                ) {
                    return@withTransaction null
                }
                val packets = database.syncQueue().activePackets().filter {
                    it.peerIdentity == peer && it.peerDevice == device &&
                        it.scopeEpoch == link.scopeEpoch
                }
                val bytes = packets.sumOf { it.envelope.size.toLong() * 2 + 2048L }
                require(bytes in 1..6291456L && packets.size in 1..64)
                JSONObject().put(
                    "grant",
                    requireNotNull(authority.grantJson)
                ).put("scope", JSONObject(requireNotNull(link.scopeJson)))
                    .put("epoch", link.scopeEpoch).put("context", context(peer, device))
                    .put("bytes", bytes).put("parts", packets.size).put("renewal", true)
            } ?: continue
            runtime.queueControl(
                peer,
                device,
                JSONObject().put("certificate", runtime.certificate()),
                JSONObject().put("authorRequest", request)
            )
            progress = true
        }
        progress
    }

    private suspend fun refreshForecast(link: SyncLinkRow) {
        if (ShareCategory.FORECASTS !in
            PartnerCodec.scope(JSONObject(requireNotNull(link.scopeJson))).categories
        ) {
            return
        }
        database.withTransaction {
            val snapshot = partner.preview(ShareScope(setOf(ShareCategory.FORECASTS)))
            snapshot.remove("calculatedAt")
            val value = canonicalJson(snapshot)
            val old = database.syncJournal().latest().lastOrNull { it.entityType == "FORECAST" }
            if (old?.payload !=
                value
            ) {
                database.syncJournal().insert(
                    SyncChangeRow(
                        entityType = "FORECAST",
                        entityKey = "current",
                        deleted = false,
                        payload = value
                    )
                )
            }
        }
    }

    private suspend fun context(peer: String, device: String): String = requireNotNull(
        partner.snapshot().identities.single {
            it.id == peer && it.exchangeEnabled
        }.devices.single {
            it.id == device && it.enabled && it.mutualConfirmed
        }.verificationContext
    )
}
