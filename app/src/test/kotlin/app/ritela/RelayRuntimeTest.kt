package app.ritela

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ritela.data.DayLogEntity
import app.ritela.data.LocalPartnerKeys
import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerCrypto
import app.ritela.data.PartnerMessage
import app.ritela.data.PartnerRepository
import app.ritela.data.sync.AutomaticSync
import app.ritela.data.sync.ForegroundSync
import app.ritela.data.sync.RelayConnections
import app.ritela.data.sync.RelayHttpExchange
import app.ritela.data.sync.RelayHttpRequest
import app.ritela.data.sync.RelayHttpResponse
import app.ritela.data.sync.RelayKeyVault
import app.ritela.data.sync.RelayRequestAuth
import app.ritela.data.sync.RelayRun
import app.ritela.data.sync.RelayRuntime
import app.ritela.data.sync.SyncPacketEncoder
import app.ritela.data.sync.SyncPreparation
import app.ritela.data.sync.SyncQueueRepository
import app.ritela.data.sync.SyncQueueStatus
import app.ritela.data.sync.SyncSettingsRepository
import app.ritela.domain.DayLog
import app.ritela.domain.Doodle
import app.ritela.domain.DoodlePoint
import app.ritela.domain.DoodleStroke
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RelayRuntimeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var time = 1800000000000L
    private data class Route(
        val receiver: String,
        val sender: String,
        val capability: String,
        val authority: String? = null
    )
    private data class Packet(
        val sequence: Long,
        val route: String,
        val id: String,
        val bytes: ByteArray
    )

    /** In-memory relay verifies the exact production API signatures, without opening a socket. */
    private inner class Server : RelayHttpExchange {
        val keys = mutableMapOf<String, String>()
        val routes = mutableMapOf<String, Route>()
        val authorities = mutableMapOf<String, String>()
        val reservations = mutableMapOf<String, String>()
        val uploadQueries = mutableMapOf<String, Map<String, String>>()
        val packets = mutableListOf<Packet>()
        val uploads = mutableListOf<Packet>()
        var calls = 0
        var sequence = 0L
        var pageLimit = 32
        var losePut = false
        var loseAck = false
        var status = 0
        var entered: CompletableDeferred<Unit>? = null
        var release: CompletableDeferred<Unit>? = null
        override suspend fun execute(
            request: RelayHttpRequest,
            authorize: suspend () -> Unit
        ): RelayHttpResponse {
            authorize()
            calls++
            entered?.complete(Unit)
            release?.await()
            authorize()
            if (status != 0) return RelayHttpResponse(status, "{}".toByteArray(), "120")
            val path = request.url.path
            val h = request.headers
            val handle = h.getValue("X-Ritela-Device")
            val registration = path == "/v1/devices" && request.method == "POST"
            val body = if (request.method ==
                "POST"
            ) {
                JSONObject(request.body.toString(Charsets.UTF_8))
            } else {
                null
            }
            val publicKey = if (registration) {
                body!!.getString(
                    "publicKey"
                )
            } else {
                keys.getValue(handle)
            }
            val canonical = RelayRequestAuth.canonical(
                request.method,
                request.url.file,
                handle,
                1,
                h.getValue("X-Ritela-Time").toLong(),
                h.getValue("X-Ritela-Request"),
                request.body,
                h.getValue("X-Ritela-Capability")
            )
            assertTrue(
                PartnerCrypto.verify(
                    PartnerCrypto.publicKey(publicKey, "EC"),
                    canonical,
                    PartnerCrypto.b64(
                        RelayRequestAuth.rawToDer(
                            PartnerCrypto.bytes(h.getValue("X-Ritela-Signature"))
                        )
                    )
                )
            )
            val result = when {
                registration -> {
                    keys[handle] = publicKey
                    JSONObject().put("handle", handle)
                }

                path.endsWith("/initialize") -> {
                    assertEquals("/v1/authorities/$handle/initialize", path)
                    val grant = body!!.getString("grant")
                    authorities[handle]?.let { assertEquals(it, grant) }
                    authorities[handle] = grant
                    JSONObject().put("initialized", true)
                }

                path.endsWith("/reserve") -> {
                    val route = path.split("/")[3]
                    assertEquals(handle, routes.getValue(route).receiver)
                    reservations[route] = body!!.getString("id")
                    JSONObject().put("reserved", true)
                }

                path == "/v1/routes" -> {
                    routes[body!!.getString("route")] = Route(
                        handle,
                        body.getString("sender"),
                        body.getString("capability"),
                        body.optString("authority", "").takeIf { it.isNotEmpty() }
                    )
                    JSONObject().put("created", true)
                }

                request.method == "PUT" -> {
                    val parts = path.split("/")
                    val route = parts[3]
                    val id = parts[5]
                    assertEquals(routes.getValue(route).sender, handle)
                    assertEquals(
                        routes.getValue(route).capability,
                        h.getValue("X-Ritela-Capability")
                    )
                    val query = request.url.query.split('&').associate {
                        val parts = it.split('=', limit = 2)
                        parts[0] to parts[1]
                    }
                    uploadQueries[id] = query
                    if (query.getValue("class") == "AUTHOR") {
                        val authority = requireNotNull(routes.getValue(route).authority)
                        assertEquals(authority, handle)
                        assertEquals("1", query["generation"])
                        assertEquals(authorities.getValue(authority), query["grant"])
                        query["reservation"]?.let { assertEquals(reservations[route], it) }
                    }
                    val old = packets.singleOrNull { it.id == id && it.route == route }
                    if (old != null) assertArrayEquals(old.bytes, request.body)
                    val packet =
                        old
                            ?: Packet(++sequence, route, id, request.body.copyOf()).also {
                                packets.add(it)
                            }
                    uploads.add(packet)
                    if (losePut) {
                        losePut = false
                        throw IOException("RELAY_IO")
                    }
                    JSONObject().put("accepted", true)
                }

                request.method == "GET" -> {
                    val cursor = request.url.query.substringAfter(
                        "cursor="
                    ).substringBefore("&").toLong()
                    val page = packets.filter {
                        it.sequence > cursor &&
                            routes.getValue(it.route).receiver == handle
                    }.take(pageLimit)
                    JSONObject().put(
                        "packets",
                        JSONArray().apply {
                            page.forEach {
                                put(
                                    JSONObject().put("sequence", it.sequence).put("route", it.route)
                                        .put(
                                            "id",
                                            it.id
                                        ).put("ciphertext", PartnerCrypto.b64(it.bytes))
                                )
                            }
                        }
                    ).put("cursor", (page.lastOrNull()?.sequence ?: cursor).toString())
                }

                path.endsWith("/ack") -> {
                    val rows = body!!.getJSONArray("packets")
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONObject(i)
                        assertEquals(handle, routes.getValue(row.getString("route")).receiver)
                        packets.removeAll {
                            it.id == row.getString("id") &&
                                it.route == row.getString("route")
                        }
                    }
                    if (loseAck) {
                        loseAck = false
                        throw IOException("RELAY_IO")
                    }
                    JSONObject().put("deleted", true)
                }

                request.method == "DELETE" -> {
                    packets.removeAll { path.endsWith("/${it.id}") }
                    JSONObject().put("deleted", true)
                }

                else -> error("Unexpected relay operation")
            }
            return RelayHttpResponse(200, result.toString().toByteArray())
        }
    }

    private inner class Phone(val server: Server) : AutoCloseable {
        val profile = UUID.randomUUID().toString()
        val name = "relay-$profile.db"
        val local = LocalPartnerKeys.create()
        val wrapping = SecretKeySpec(PartnerCrypto.random(32), "AES")
        val vault = RelayKeyVault(context, profile) { wrapping }
        var db = syncTestDatabase(context, name)
        var partner = repository()
        var selected = true
        var online = true
        var runtime = runtime()
        lateinit var automatic: AutomaticSync
        init {
            attach()
        }
        private fun repository() = PartnerRepository(db, { local }, {}, { time })
        private fun runtime() = RelayRuntime(
            context, profile, db, { partner }, { selected },
            "https://relay.example", vault, server, { time }
        )
        private fun attach() {
            partner.relaySetup = { peer, device, value -> runtime.acceptSetup(peer, device, value) }
            val connections = RelayConnections(
                db,
                partner,
                runtime,
                "https://relay.example",
                { online }
            )
            partner.relayInvitation = connections::invitation
            partner.relayPairing = connections::accepted
            automatic = AutomaticSync(db, partner, runtime)
            runtime.prepareAutomatic = automatic::prepare
            runtime.authorizeAutomatic = automatic::authorized
            runtime.afterDelivery = automatic::refreshReservations
            partner.automaticIncoming = automatic::verifyIncoming
        }
        suspend fun activate() {
            runtime.activate("a".repeat(64), true)
            assertEquals(RelayRun.SUCCESS, runtime.runOnce())
        }
        suspend fun reopen() {
            runtime.close()
            db.close()
            db = syncTestDatabase(context, name)
            partner = repository()
            runtime = runtime()
            attach()
        }
        override fun close() {
            db.close()
            context.deleteDatabase(name)
            File(context.filesDir, "relay-$profile.keys").delete()
        }
    }
    private suspend fun setup(a: Phone, b: Phone) {
        confirmedPair(a.partner, b.local, "B")
        confirmedPair(b.partner, a.local, "A")
        a.activate()
        b.activate()
        // The very same authenticated file/Bluetooth setup exposed in Partner settings.
        repeat(2) {
            val fromA = a.runtime.prepareSetup(b.local.identityId, b.local.deviceId)
            val fromB = b.runtime.prepareSetup(a.local.identityId, a.local.deviceId)
            b.partner.receive(fromA.envelope.toByteArray())
            a.partner.receive(fromB.envelope.toByteArray())
            assertFalse(fromA.body.contains("relay"))
            assertFalse(fromB.body.contains("relay"))
        }
    }
    private suspend fun send(a: Phone, b: Phone): PartnerMessage = a.partner.confirmData(
        b.local.identityId,
        b.local.deviceId,
        ShareScope(setOf(ShareCategory.PERIODS)),
        "SYNTHETIC_SECRET"
    )

    @Test fun foregroundOpenDeliversAcrossProfileSwitchAndBudgetSurvivesControllerRecreation() =
        runBlocking {
            val server = Server()
            Phone(server).use { a ->
                Phone(server).use { b ->
                    setup(a, b)
                    b.selected = false
                    val message = send(a, b)
                    assertEquals(
                        RelayRun.SUCCESS,
                        ForegroundSync(context, a.profile, a.runtime) { time }.check()
                    )
                    val calls = server.calls
                    assertEquals(
                        RelayRun.IDLE,
                        ForegroundSync(context, b.profile, b.runtime) { time }.check()
                    )
                    assertEquals(calls, server.calls)
                    a.selected = false
                    b.selected = true
                    assertEquals(
                        RelayRun.MORE,
                        ForegroundSync(context, b.profile, b.runtime) { time }.check()
                    )
                    assertNotNull(b.db.partner().message(message.id))
                    a.selected = true
                    b.selected = false
                    ForegroundSync(context, a.profile, a.runtime) { time }.check()
                    assertTrue(a.partner.retry(message.id).delivered)
                    repeat(22) {
                        ForegroundSync(context, a.profile, a.runtime) { time }.check()
                    }
                    val exhaustedCalls = server.calls
                    assertEquals(
                        RelayRun.IDLE,
                        ForegroundSync(context, a.profile, a.runtime) { time }.check()
                    )
                    assertEquals(exhaustedCalls, server.calls)
                    val next = send(a, b)
                    ForegroundSync(context, a.profile, a.runtime) { time }.send()
                    assertEquals("STORED", requireNotNull(a.db.relay().packet(next.id)).state)
                }
            }
        }

    @Test fun invitationWizardEstablishesBothDirectionsWithoutManualSetupFiles() = runBlocking {
        val server = Server()
        Phone(server).use { a ->
            Phone(server).use { b ->
                val offline = a.partner.invitation("A")
                assertFalse(JSONObject(JSONObject(offline).getString("body")).has("relay"))
                assertEquals(0, server.calls)
                a.activate()
                b.activate()
                val first = b.partner.inspectInvitation(a.partner.invitation("A"))
                b.partner.pair(first)
                assertFalse(
                    b.partner.snapshot().identities.single().devices.single().mutualConfirmed
                )
                assertFalse(b.runtime.linked(a.local.identityId, a.local.deviceId))
                val response = a.partner.inspectInvitation(b.partner.invitation("B", first))
                a.partner.pair(response)
                val confirmation = a.partner.invitation("A", response)
                for (route in server.routes.values) {
                    assertFalse(
                        confirmation.contains(route.capability)
                    )
                }
                assertTrue(
                    confirmation.toByteArray().size <=
                        app.ritela.data.PartnerInvitationFile.MAX_BYTES
                )
                val inspected = b.partner.inspectInvitation(confirmation)
                assertTrue(b.partner.acceptKnown(inspected))
                val pending = b.db.syncMessageIntents().count()
                assertTrue(b.partner.acceptKnown(inspected))
                assertEquals(pending, b.db.syncMessageIntents().count())
                b.runtime.runOnce()
                a.runtime.runOnce()
                assertTrue(a.runtime.linked(b.local.identityId, b.local.deviceId))
                assertTrue(b.runtime.linked(a.local.identityId, a.local.deviceId))
                val message = send(a, b)
                a.runtime.runOnce()
                b.runtime.runOnce()
                a.runtime.runOnce()
                assertTrue(a.partner.retry(message.id).delivered)
            }
        }
    }

    @Test fun manualPairingAndDataStillWorkOfflineAfterPilotActivation() = runBlocking {
        val server = Server()
        Phone(server).use { a ->
            Phone(server).use { b ->
                a.activate()
                b.activate()
                a.online = false
                b.online = false
                val calls = server.calls
                val first = b.partner.inspectInvitation(a.partner.invitation("A"))
                b.partner.pair(first)
                val response = a.partner.inspectInvitation(b.partner.invitation("B", first))
                a.partner.pair(response)
                assertTrue(
                    b.partner.acceptKnown(
                        b.partner.inspectInvitation(a.partner.invitation("A", response))
                    )
                )
                val message = send(a, b)
                assertEquals("DATA", b.partner.receive(message.envelope.toByteArray()).message.kind)
                assertEquals(calls, server.calls)
                assertTrue(server.routes.isEmpty())
            }
        }
    }

    @Test fun malformedControlIsRejectedBeforeItCanPoisonChatState() = runBlocking {
        val server = Server()
        Phone(server).use { a ->
            Phone(server).use { b ->
                setup(a, b)
                val bad = a.runtime.queueControl(
                    b.local.identityId,
                    b.local.deviceId,
                    JSONObject().put("certificate", a.runtime.certificate()),
                    JSONObject().put("authorRequest", 1)
                )
                a.runtime.runOnce()
                assertEquals(RelayRun.PAUSED, b.runtime.runOnce())
                assertNull(b.db.partner().message(bad.id))
                assertTrue(runCatching { b.partner.messages.first() }.isSuccess)
                assertEquals("VALIDATION", requireNotNull(b.db.relay().config()).error)
            }
        }
    }

    @Test fun knownContactsRepairExpiredRoutesWithAnInvitationAndOneResponse() = runBlocking {
        val server = Server()
        Phone(server).use { a ->
            Phone(server).use { b ->
                setup(a, b)
                time += 7 * 86400000L + 1
                assertFalse(a.runtime.linked(b.local.identityId, b.local.deviceId))
                val invitation = b.partner.inspectInvitation(a.partner.invitation("A"))
                assertTrue(b.partner.acceptKnown(invitation))
                val response = a.partner.inspectInvitation(b.partner.invitation("B", invitation))
                assertTrue(a.partner.acceptKnown(response))
                a.runtime.runOnce()
                b.runtime.runOnce()
                a.runtime.runOnce()
                assertTrue(a.runtime.linked(b.local.identityId, b.local.deviceId))
                assertTrue(b.runtime.linked(a.local.identityId, a.local.deviceId))
            }
        }
    }

    @Test fun delayedBaselineRenewsWithoutRepeatingConsentAfterReopen() = runBlocking {
        val server = Server()
        Phone(server).use { a ->
            Phone(server).use { b ->
                setup(a, b)
                val selection = ShareScope(setOf(ShareCategory.SECTIONS), sections = setOf("note"))
                a.db.dayLogs().save(
                    DayLogEntity.from(DayLog(LocalDate.ofEpochDay(10), note = "DELAYED"))
                )
                a.automatic.enable(b.local.identityId, b.local.deviceId, selection)
                a.runtime.runOnce()
                b.runtime.runOnce()
                val request = b.db.partner().controls(
                    a.local.identityId,
                    a.local.deviceId,
                    false
                ).single {
                    JSONObject(it.body).has("authorRequest")
                }
                b.automatic.approve(request.id)
                b.runtime.runOnce()
                time += 600001
                a.reopen()
                b.reopen()
                repeat(6) {
                    a.runtime.runOnce()
                    b.runtime.runOnce()
                }
                assertTrue(
                    requireNotNull(
                        b.db.syncReceive().state(a.local.identityId)
                    ).snapshot.contains("DELAYED")
                )
                assertEquals(1, b.partner.messages.first().count { it.kind == "SYNC_REQUEST" })
                assertTrue(
                    a.db.partner().controls(b.local.identityId, b.local.deviceId, true).any {
                        JSONObject(it.body).optJSONObject("authorRequest")?.optBoolean("renewal") ==
                            true
                    }
                )
                time += 7 * 86400000L + 1
                a.db.dayLogs().save(
                    DayLogEntity.from(DayLog(LocalDate.ofEpochDay(10), note = "AFTER_REPAIR"))
                )
                assertEquals(RelayRun.SUCCESS, a.runtime.runOnce())
                val invitation = b.partner.inspectInvitation(a.partner.invitation("A"))
                assertTrue(b.partner.acceptKnown(invitation))
                val response = a.partner.inspectInvitation(b.partner.invitation("B", invitation))
                assertTrue(a.partner.acceptKnown(response))
                repeat(7) {
                    a.runtime.runOnce()
                    b.runtime.runOnce()
                }
                assertTrue(
                    requireNotNull(
                        b.db.syncReceive().state(a.local.identityId)
                    ).snapshot.contains("AFTER_REPAIR")
                )
                assertEquals(1, b.partner.messages.first().count { it.kind == "SYNC_REQUEST" })
            }
        }
    }

    @Test fun approvedDiaryChangesAndDeletesReachChatWithoutAnotherConfirmation() = runBlocking {
        val server = Server()
        Phone(server).use { a ->
            Phone(server).use { b ->
                setup(a, b)
                val selection =
                    ShareScope(
                        setOf(ShareCategory.PERIODS, ShareCategory.SECTIONS),
                        sections = setOf("note")
                    )
                a.db.dayLogs().save(
                    DayLogEntity.from(
                        DayLog(
                            LocalDate.ofEpochDay(10),
                            note = "SHARED",
                            custom = mapOf("private" to setOf("PRIVATE_VALUE"))
                        )
                    )
                )
                a.automatic.enable(b.local.identityId, b.local.deviceId, selection)
                a.runtime.runOnce()
                assertEquals(
                    0,
                    server.uploads.count {
                        a.db.partner().message(it.id)?.kind == "SYNC"
                    }
                )
                b.runtime.runOnce()
                assertNull(b.db.syncReceive().state(a.local.identityId))
                val request = b.db.partner().controls(
                    a.local.identityId,
                    a.local.deviceId,
                    false
                ).single {
                    JSONObject(it.body).has("authorRequest")
                }
                b.automatic.approve(request.id)
                assertTrue(b.partner.messages.first().any { it.kind == "SYNC_REQUEST" })
                val routes = server.routes.size
                b.automatic.approve(request.id)
                assertEquals(routes, server.routes.size)
                b.partner.deleteMessage(request.id)
                assertTrue(requireNotNull(b.db.partner().message(request.id)).hiddenFromChat)
                val widened = JSONObject(
                    JSONObject(request.body).getJSONObject("authorRequest").toString()
                )
                    .put(
                        "scope",
                        PartnerCodec.scope(
                            selection.copy(
                                categories =
                                    selection.categories + ShareCategory.FORECASTS
                            )
                        )
                    )
                    .put("renewal", true)
                val denied = a.runtime.queueControl(
                    b.local.identityId,
                    b.local.deviceId,
                    JSONObject().put("certificate", a.runtime.certificate()),
                    JSONObject().put("authorRequest", widened)
                )
                a.runtime.runOnce()
                b.runtime.runOnce()
                assertFalse(b.automatic.isApproved(denied.id))
                assertEquals(routes, server.routes.size)
                b.runtime.runOnce()
                a.runtime.runOnce()
                b.runtime.runOnce()
                a.runtime.runOnce()
                val initial = requireNotNull(b.db.syncReceive().state(a.local.identityId)).snapshot
                assertTrue(initial.contains("SHARED"))
                assertFalse(initial.contains("PRIVATE_VALUE"))
                time += 1000
                a.db.dayLogs().save(
                    DayLogEntity.from(
                        DayLog(
                            LocalDate.ofEpochDay(10),
                            note = "UPDATED",
                            custom = mapOf("private" to setOf("NEW_PRIVATE_VALUE"))
                        )
                    )
                )
                a.runtime.runOnce()
                b.runtime.runOnce()
                a.runtime.runOnce()
                val updated = requireNotNull(b.db.syncReceive().state(a.local.identityId)).snapshot
                assertTrue(updated.contains("UPDATED"))
                assertFalse(updated.contains("NEW_PRIVATE_VALUE"))
                assertEquals(
                    2,
                    b.db.partner().history(a.local.identityId).count {
                        it.id.startsWith("sync-update:")
                    }
                )
                val link = a.db.syncState().links().single()
                assertTrue(
                    runCatching {
                        b.automatic.verifyIncoming(
                            a.local.identityId,
                            a.local.deviceId,
                            JSONObject().put(
                                "epoch",
                                link.scopeEpoch
                            ).put("grant", a.db.syncState().authorities().single().grantDigest)
                                .put(
                                    "scope",
                                    PartnerCodec.scope(
                                        ShareScope(
                                            setOf(ShareCategory.SECTIONS),
                                            sections = setOf("note", "private")
                                        )
                                    )
                                )
                        )
                    }.isFailure
                )
                a.db.dayLogs().delete(10)
                a.runtime.runOnce()
                b.runtime.runOnce()
                a.runtime.runOnce()
                assertEquals(
                    0,
                    JSONObject(
                        requireNotNull(b.db.syncReceive().state(a.local.identityId)).snapshot
                    ).getJSONArray("days").length()
                )
                assertEquals(
                    3,
                    b.db.partner().history(a.local.identityId).count {
                        it.id.startsWith("sync-update:")
                    }
                )
                a.automatic.pause(b.local.identityId, b.local.deviceId)
                val uploads = server.uploads.size
                a.db.dayLogs().save(
                    DayLogEntity.from(DayLog(LocalDate.ofEpochDay(10), note = "AFTER_PAUSE"))
                )
                a.runtime.runOnce()
                assertEquals(uploads, server.uploads.size)
            }
        }
    }

    @Test fun completeMessageAndDoodleDeliveryRequiresApplicationReceiptAndKeepsManualReplay() =
        runBlocking {
            val server = Server()
            Phone(server).use { a ->
                Phone(server).use { b ->
                    setup(a, b)
                    a.db.periods().addIfSeparate(
                        app.ritela.data.PeriodEntity(
                            UUID.randomUUID().toString(),
                            20000,
                            20004,
                            time,
                            time
                        )
                    )
                    val message = send(a, b)
                    assertEquals(RelayRun.SUCCESS, a.runtime.runOnce())
                    assertFalse(a.partner.retry(message.id).delivered)
                    assertEquals(
                        SyncQueueStatus.AWAITING_RECEIPT,
                        SyncSettingsRepository(a.db).read().entries
                            .single { it.key == "m:${message.id}" }.status
                    )
                    val stored = a.db.relay().packet(message.id)!!
                    assertFalse(
                        stored.envelope.toString(Charsets.UTF_8).contains("SYNTHETIC_SECRET")
                    )
                    assertEquals(RelayRun.MORE, b.runtime.runOnce())
                    val received = b.db.partner().message(message.id)!!
                    assertEquals(
                        20000L,
                        JSONObject(
                            received.body
                        ).getJSONArray("periods").getJSONObject(0).getLong("start")
                    )
                    assertEquals(RelayRun.MORE, a.runtime.runOnce())
                    assertTrue(a.partner.retry(message.id).delivered)
                    assertEquals("ACKED", a.db.relay().packet(message.id)!!.state)
                    assertEquals(
                        received.received,
                        b.partner.receive(message.envelope.toByteArray()).message.received
                    )
                    val doodle = a.partner.prepareDoodle(
                        b.local.identityId,
                        b.local.deviceId,
                        Doodle(
                            listOf(DoodleStroke(listOf(DoodlePoint(.2f, .3f)), 0xffee5577, .01f)),
                            "Synthetic"
                        )
                    )
                    a.runtime.runOnce()
                    b.runtime.runOnce()
                    val opened = b.partner.openDoodle(doodle.id)
                    assertNotNull(opened)
                    b.runtime.runOnce()
                    a.runtime.runOnce()
                    assertTrue(a.partner.retry(doodle.id).opened)
                    assertEquals(0, a.db.syncState().links().single().automaticEnabled)
                    assertEquals("UNINITIALIZED", a.db.syncState().authorities().single().state)
                    val secret = server.routes.values.first().capability
                    assertTrue(
                        a.db.relay().routes().all {
                            !it.credentials.toString(Charsets.UTF_8).contains(secret)
                        }
                    )
                }
            }
        }

    @Test fun lostStorageResponseAndDatabaseRestartRetryIdenticalCiphertext() = runBlocking {
        val server = Server()
        Phone(server).use { a ->
            Phone(server).use { b ->
                setup(a, b)
                val message = send(a, b)
                assertTrue(a.runtime.canDeliver(message))
                server.losePut = true
                assertEquals(RelayRun.RETRY, a.runtime.runOnce())
                val original = a.db.relay().packet(message.id)!!.envelope.copyOf()
                assertEquals("PENDING", a.db.relay().packet(message.id)!!.state)
                val deadline = a.db.relay().config()!!.nextAttempt
                a.reopen()
                val calls = server.calls
                assertEquals(RelayRun.RETRY, a.runtime.runOnce())
                assertEquals(calls, server.calls)
                time = deadline + 1
                assertEquals(RelayRun.SUCCESS, a.runtime.runOnce())
                assertArrayEquals(original, server.uploads.last().bytes)
                assertEquals(1, server.packets.count { it.id == message.id })
                b.runtime.runOnce()
                a.runtime.runOnce()
                assertTrue(a.partner.retry(message.id).delivered)
            }
        }
    }

    @Test fun optOutAndProfileSwitchPerformNoRequestsOrKeyCreation() = runBlocking {
        val server = Server()
        Phone(server).use { phone ->
            assertEquals(RelayRun.IDLE, phone.runtime.runOnce())
            assertEquals(0, server.calls)
            assertNull(phone.vault.load())
            assertTrue(runCatching { phone.runtime.activate("a".repeat(64), false) }.isFailure)
            assertNull(phone.vault.load())
            phone.activate()
            phone.selected = false
            val calls = server.calls
            assertEquals(RelayRun.IDLE, phone.runtime.runOnce())
            assertEquals(calls, server.calls)
        }
    }

    @Test fun retryAfterSurvivesRestartAndNeverAllowsEarlyForegroundOverride() = runBlocking {
        val server = Server()
        Phone(server).use { phone ->
            phone.activate()
            server.status = 429
            assertEquals(RelayRun.RETRY, phone.runtime.runOnce())
            val deadline = phone.db.relay().config()!!.nextAttempt
            assertTrue(deadline >= time + 120000)
            phone.reopen()
            val calls = server.calls
            assertEquals(RelayRun.RETRY, phone.runtime.runOnce())
            assertEquals(calls, server.calls)
            time = deadline + 1
            server.status = 0
            assertEquals(RelayRun.SUCCESS, phone.runtime.runOnce())
        }
    }

    @Test fun invalidSecondPacketRollsBackWholePageAndDoesNotAckOrAdvanceCursor() = runBlocking {
        val server = Server()
        Phone(server).use { a ->
            Phone(server).use { b ->
                setup(a, b)
                val first = send(a, b)
                val second = send(a, b)
                a.runtime.runOnce()
                val packet = server.packets.single { it.id == second.id }
                server.packets[server.packets.indexOf(packet)] =
                    packet.copy(bytes = byteArrayOf(1, 2, 3))
                assertEquals(RelayRun.PAUSED, b.runtime.runOnce())
                assertNull(b.db.partner().message(first.id))
                assertEquals(0L, b.db.relay().config()!!.cursor)
                assertEquals(2, server.packets.size)
            }
        }
    }

    @Test fun cancellationJoinsInFlightRequestBeforeDatabaseCanBeClosed() = runBlocking {
        val server = Server()
        Phone(server).use { phone ->
            phone.activate()
            server.entered = CompletableDeferred()
            server.release = CompletableDeferred()
            val pending = async { phone.runtime.runOnce() }
            withTimeout(5000) { server.entered!!.await() }
            withTimeout(5000) { phone.runtime.cancelRequests() }
            pending.join()
            assertTrue(pending.isCancelled)
            assertEquals(null, phone.db.relay().config()!!.error)
        }
    }

    @Test fun revokedStoredPacketIsRemovedFromRelayWithoutErasingManualHistory() = runBlocking {
        val server = Server()
        Phone(server).use { a ->
            Phone(server).use { b ->
                setup(a, b)
                val message = send(a, b)
                a.runtime.runOnce()
                a.partner.enable(b.local.identityId, false)
                assertFalse(a.runtime.canDeliver(message))
                assertEquals(RelayRun.SUCCESS, a.runtime.runOnce())
                assertTrue(server.packets.isEmpty())
                assertEquals("CANCELLED", a.db.relay().packet(message.id)!!.state)
                assertEquals(message.id, a.db.partner().message(message.id)!!.id)
            }
        }
    }

    @Test fun largeQueueContinuesWithoutStarvation() = runBlocking {
        val server = Server()
        Phone(server).use { a ->
            Phone(server).use { b ->
                setup(a, b)
                val messages = List(40) { send(a, b) }
                assertEquals(RelayRun.MORE, a.runtime.runOnce())
                assertEquals(32, server.uploads.size)
                assertEquals(RelayRun.SUCCESS, a.runtime.runOnce())
                assertEquals(40, server.uploads.size)
                assertEquals(RelayRun.MORE, b.runtime.runOnce())
                b.runtime.runOnce()
                a.runtime.runOnce()
                a.runtime.runOnce()
                assertTrue(messages.all { a.partner.retry(it.id).delivered })
                assertEquals(40, b.db.relay().receivedCount() - 2)
            }
        }
    }

    @Test fun manualBeforeHttpPreservesFirstReceiveTime() = runBlocking {
        val server = Server()
        Phone(server).use { a ->
            Phone(server).use { b ->
                setup(a, b)
                val message = send(a, b)
                val first = b.partner.receive(message.envelope.toByteArray()).message
                time += 60000
                a.runtime.runOnce()
                b.runtime.runOnce()
                a.runtime.runOnce()
                assertEquals(first, b.db.partner().message(message.id))
                assertTrue(a.partner.retry(message.id).delivered)
            }
        }
    }

    @Test fun lostStorageAckAfterApplyRecoversWithoutLosingReceipt() = runBlocking {
        val server = Server()
        Phone(server).use { a ->
            Phone(server).use { b ->
                setup(a, b)
                val message = send(a, b)
                a.runtime.runOnce()
                server.loseAck = true
                assertEquals(RelayRun.RETRY, b.runtime.runOnce())
                val first = b.db.partner().message(message.id)!!
                assertEquals(0L, b.db.relay().config()!!.cursor)
                assertTrue(server.packets.isEmpty())
                val deadline = b.db.relay().config()!!.nextAttempt
                b.reopen()
                time = deadline + 1
                assertEquals(RelayRun.SUCCESS, b.runtime.runOnce())
                a.runtime.runOnce()
                assertEquals(first, b.db.partner().message(message.id))
                assertTrue(a.partner.retry(message.id).delivered)
            }
        }
    }

    @Test fun byteLimitedShortPageStillSchedulesItsNextFetch() = runBlocking {
        val server = Server()
        Phone(server).use { a ->
            Phone(server).use { b ->
                setup(a, b)
                send(a, b)
                send(a, b)
                a.runtime.runOnce()
                server.pageLimit = 1
                assertEquals(RelayRun.MORE, b.runtime.runOnce())
                assertEquals(RelayRun.MORE, b.runtime.runOnce())
                assertEquals(RelayRun.SUCCESS, b.runtime.runOnce())
            }
        }
    }

    @Test fun missingRouteDoesNotStarveAnotherPeer() = runBlocking {
        val server = Server()
        Phone(server).use { a ->
            Phone(server).use { b ->
                Phone(server).use { c ->
                    setup(a, b)
                    a.db.openHelper.writableDatabase.execSQL(
                        "DELETE FROM relay_routes WHERE incoming=0"
                    )
                    repeat(32) { send(a, b) }
                    assertEquals(RelayRun.SUCCESS, a.runtime.runOnce())
                    assertTrue(server.uploads.isEmpty())
                    setup(a, c)
                    val message = send(a, c)
                    assertEquals(RelayRun.SUCCESS, a.runtime.runOnce())
                    assertEquals(listOf(message.id), server.uploads.map { it.id })
                }
            }
        }
    }

    @Test fun recordOutboxAppliesBeforeE2eReceipt() = runBlocking {
        val server = Server()
        Phone(server).use { a ->
            Phone(server).use { b ->
                setup(a, b)
                val scope = ShareScope(
                    setOf(ShareCategory.PERIODS, ShareCategory.SECTIONS),
                    sections = setOf("note")
                )
                a.partner.grant(b.local.identityId, scope)
                val grant = a.partner.initializeSyncAuthor()
                b.partner.approveSyncAuthor(a.local.identityId, grant)
                val authority = a.runtime.registerAuthorFence()
                val route = b.runtime.createReceiveRoute(
                    a.local.identityId,
                    a.local.deviceId,
                    a.runtime.certificate(),
                    authority,
                    65536,
                    1
                )
                a.runtime.installSendRoute(route)
                a.db.openHelper.writableDatabase.execSQL(
                    "UPDATE sync_links SET automaticEnabled=1,scopeJson=? WHERE peerIdentity=?",
                    arrayOf(PartnerCodec.scope(scope).toString(), b.local.identityId)
                )
                a.db.dayLogs().save(
                    DayLogEntity.from(
                        DayLog(
                            LocalDate.ofEpochDay(10),
                            note = "SYNTHETIC_SYNC_NOTE"
                        )
                    )
                )
                val queue = SyncQueueRepository(a.db, SyncPacketEncoder(a.partner::encodeSyncDraft))
                assertEquals(
                    SyncPreparation.ENQUEUED,
                    queue.prepare(b.local.identityId, b.local.deviceId)
                )
                val id = a.db.syncQueue().packets().single().packetId
                assertEquals(RelayRun.SUCCESS, a.runtime.runOnce())
                assertEquals("STORED", a.db.syncQueue().packets().single().state)
                assertFalse(a.db.partner().message(id)!!.delivered)
                assertEquals(RelayRun.MORE, b.runtime.runOnce())
                val snapshot = JSONObject(b.db.syncReceive().states().first().single().snapshot)
                assertEquals(
                    "SYNTHETIC_SYNC_NOTE",
                    snapshot.getJSONArray("days").getJSONObject(0).getString("note")
                )
                a.runtime.runOnce()
                assertTrue(a.db.partner().message(id)!!.delivered)
                assertEquals("ACKED", a.db.relay().packet(id)!!.state)
                assertTrue(a.db.syncQueue().packets().single().envelope.isEmpty())
                assertTrue(server.uploadQueries.getValue(id).containsKey("reservation"))
                time += 600001
                a.db.dayLogs().save(
                    DayLogEntity.from(
                        DayLog(
                            LocalDate.ofEpochDay(10),
                            note = "SYNTHETIC_SYNC_EDIT"
                        )
                    )
                )
                assertEquals(
                    SyncPreparation.ENQUEUED,
                    queue.prepare(b.local.identityId, b.local.deviceId)
                )
                val delta = a.db.syncQueue().packets().single { it.packetId != id }.packetId
                a.runtime.runOnce()
                assertFalse(server.uploadQueries.getValue(delta).containsKey("reservation"))
                b.runtime.runOnce()
                a.runtime.runOnce()
                assertTrue(a.db.partner().message(delta)!!.delivered)
                assertEquals(
                    "SYNTHETIC_SYNC_EDIT",
                    JSONObject(
                        b.db.syncReceive().states().first().single().snapshot
                    ).getJSONArray("days").getJSONObject(0).getString("note")
                )
            }
        }
    }

    @Test fun originChangeFailsClosedForEveryControlAndDeliveryRequest() = runBlocking {
        val server = Server()
        Phone(server).use { phone ->
            phone.activate()
            val config = phone.db.relay().config()!!
            phone.db.relay().config(config.copy(origin = "https://other.example"))
            val calls = server.calls
            assertEquals(RelayRun.IDLE, phone.runtime.runOnce())
            assertTrue(runCatching { phone.runtime.certificate() }.isFailure)
            assertTrue(runCatching { phone.runtime.registerAuthorFence() }.isFailure)
            assertTrue(
                runCatching {
                    phone.runtime.activate("a".repeat(64), true)
                }.isFailure
            )
            assertEquals(calls, server.calls)
        }
    }
}
