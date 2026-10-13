package app.ritela

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import app.ritela.data.PartnerMessage
import app.ritela.domain.PartnerContact
import app.ritela.domain.PartnerSharing
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import app.ritela.ui.ConversationAction
import app.ritela.ui.ConversationPreviewContent
import app.ritela.ui.HomeTheme
import app.ritela.ui.LocalProfileSession
import app.ritela.ui.PartnerChats
import app.ritela.ui.PartnerConversation
import app.ritela.ui.PartnerFormatter
import app.ritela.ui.PartnerMessageBubble
import app.ritela.ui.PartnerScopeDialog
import app.ritela.ui.PartnerScreen
import app.ritela.ui.conversationMessages
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-rRU-w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PartnerMessengerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun ready() {
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("nav-today").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun rendersConversationStatesAndLargeText() {
        ready()
        val kind = mutableStateOf("empty")
        val large = mutableStateOf(false)
        compose.activity.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    LocalDensity.current.density,
                    if (large.value) 2f else 1f
                )
            ) { ConversationPreviewContent(kind.value) }
        }
        listOf("empty", "data", "doodle", "pending", "multiple").forEach {
            compose.runOnIdle { kind.value = it }
            compose.onNodeWithTag("partner-composer").assertIsDisplayed()
            compose.onNodeWithTag("partner-profile").assertIsDisplayed()
            capture("partner-messenger-$it")
        }
        compose.runOnIdle {
            large.value = true
            kind.value = "multiple"
        }
        compose.onNodeWithTag("partner-composer").assertIsDisplayed()
        capture("partner-messenger-large")
    }

    @Test fun dialogListOpensAChatAndBackRestoresItsScrollPosition() {
        ready()
        val first = PartnerContact("first", "Margo", setOf("peer"))
        val second = PartnerContact("second", "Alex", setOf("other"))
        val opened = mutableStateOf<String?>(null)
        val messages = (1..60).map { message("$it", "peer", it.toLong() * 60000) }
        compose.activity.setContent {
            HomeTheme {
                PartnerChats(
                    listOf(first, second), opened.value, messages, "Not connected", false, true,
                    1791792000000, null, { opened.value = it }, { opened.value = null }, {}, {},
                    {}, {}
                )
            }
        }
        compose.onNodeWithTag("partner-dialog-list").assertIsDisplayed()
        compose.onNodeWithTag("partner-composer").assertDoesNotExist()
        compose.onNodeWithText("Alex").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.partner_no_messages))
            .assertIsDisplayed()
        val row = compose.onNodeWithTag("partner-dialog-second")
        row.assertHasClickAction()
        val density = compose.activity.resources.displayMetrics.density
        val height = row.fetchSemanticsNode().boundsInRoot.height / density
        assertTrue(height in 76f..88f)
        compose.onNodeWithTag("partner-dialog-first").performClick()
        compose.onNodeWithTag("partner-message-list")
            .performScrollToNode(hasTestTag("partner-message-1"))
        compose.onNodeWithTag("partner-message-1").assertIsDisplayed()
        compose.onNodeWithTag("partner-back").performClick()
        compose.onNodeWithTag("partner-dialog-list").assertIsDisplayed()
        compose.onNodeWithTag("partner-dialog-first").performClick()
        compose.onNodeWithTag("partner-message-1").assertIsDisplayed()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithTag("partner-dialog-list").assertIsDisplayed()
        compose.onNodeWithTag("partner-list-menu").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.partner_delete))
            .assertIsDisplayed()
    }

    @Test fun composerAndHeaderStayFixedWhileMessagesScrollAndActionsReferToSelectedPartner() {
        ready()
        val first = PartnerContact("first", "Margo", setOf("peer"))
        val second = PartnerContact("second", "Alex", setOf("other"))
        val messages = (1..60).map { message("$it", "peer", it.toLong() * 60000) }
        var event: ConversationAction? = null
        var profile = ""
        compose.activity.setContent {
            HomeTheme {
                PartnerConversation(
                    first, messages, "Not connected", false, true, 1791792000000, {}, {
                        profile = "first"
                    }, { event = it }, {}, {}
                )
            }
        }
        compose.onNodeWithTag(
            "partner-message-list"
        ).performScrollToNode(hasTestTag("partner-message-1"))
        compose.onNodeWithTag("partner-composer").assertIsDisplayed()
        compose.onNodeWithTag("partner-profile").assertIsDisplayed().performClick()
        assertEquals("first", profile)
        compose.onNodeWithTag("partner-create-message").performClick()
        assertEquals(ConversationAction.SHARE, event)
        compose.onNodeWithTag("partner-add-action").performClick()
        compose.onNodeWithText(
            compose.activity.getString(R.string.partner_send_doodle)
        ).performClick()
        assertEquals(ConversationAction.DOODLE, event)
        compose.onNodeWithTag("partner-add-action").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.partner_import)).performClick()
        assertEquals(ConversationAction.FILE, event)
    }

    @Test
    @Config(qualifiers = "en-rUS-w320dp-h640dp")
    fun narrowLargeTextKeepsActionsAccessible() {
        ready()
        compose.activity.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f)
            ) {
                ConversationPreviewContent("multiple")
            }
        }
        compose.onNodeWithTag("partner-composer").assertIsDisplayed()
        compose.onNodeWithTag("partner-profile").assertIsDisplayed()
        capture("partner-messenger-narrow-english")
        compose.onNodeWithTag("partner-add-action").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.partner_import)).performClick()
    }

    @Test fun selectionRequiresConfirmationAndCancellationDoesNotPrepare() {
        ready()
        val session = (compose.activity.application as RitelaApplication).profiles.session.value
        val layout = session.model.uiState.value.journalLayout
        var prepared = false
        val showing = mutableStateOf(true)
        compose.activity.setContent {
            CompositionLocalProvider(LocalProfileSession provides session) {
                HomeTheme {
                    if (showing.value) {
                        PartnerScopeDialog(
                            layout,
                            ShareScope(setOf(ShareCategory.PERIODS)),
                            { showing.value = false },
                            "Margo"
                        ) { _ -> prepared = true }
                    }
                }
            }
        }
        compose.waitUntil(10000) {
            compose.onNodeWithText(compose.activity.getString(R.string.partner_prepare))
                .fetchSemanticsNode().config.contains(
                    androidx.compose.ui.semantics.SemanticsProperties.Disabled
                ).not()
        }
        assertFalse(prepared)
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
        assertFalse(prepared)
        compose.runOnIdle { showing.value = true }
        compose.waitUntil(10000) {
            !compose.onNodeWithText(compose.activity.getString(R.string.partner_prepare))
                .fetchSemanticsNode().config.contains(
                    androidx.compose.ui.semantics.SemanticsProperties.Disabled
                )
        }
        compose.onNodeWithText(
            compose.activity.getString(R.string.partner_prepare)
        ).performClick()
        assertTrue(prepared)
    }

    @Test fun dataBubbleShowsWhenTheRecordWasCreatedAndKeepsReceiptBelow() {
        ready()
        val created = 1_700_000_000_000L
        val received = created + 90_000_000L
        val packet = message("packet", "peer", created).copy(
            received = received,
            delivered = true,
            body = "{\"periods\":[{\"start\":20730}],\"forecasts\":[{\"start\":20758}]," +
                "\"days\":[{\"day\":20740}]}"
        )
        compose.activity.setContent {
            HomeTheme { PartnerMessageBubble(packet, received, {}, {}, true) }
        }
        val formatter = PartnerFormatter(compose.activity.resources)
        compose.onNodeWithText(
            compose.activity.getString(R.string.partner_data_recorded, formatter.moment(created))
        ).assertIsDisplayed()
        compose.onNodeWithText(
            compose.activity.getString(R.string.partner_data)
        ).assertDoesNotExist()
        listOf(
            R.string.partner_chat_periods to 1,
            R.string.partner_chat_forecasts to 1,
            R.string.partner_chat_days to 1
        ).forEach { (label, count) ->
            compose.onNodeWithText(compose.activity.getString(label, count), substring = true)
                .assertIsDisplayed()
        }
        compose.onNodeWithText(compose.activity.getString(R.string.partner_view_message))
            .assertIsDisplayed()
        compose.onNodeWithText(formatter.chatStatus(packet, dated = true)).assertIsDisplayed()
        compose.onNodeWithText(formatter.chatStatus(packet)).assertDoesNotExist()
    }

    @Test fun dataBubbleShowsTheMessageAndAttachedDoodle() {
        ready()
        val doodle = app.ritela.domain.Doodle(
            listOf(
                app.ritela.domain.DoodleStroke(
                    listOf(
                        app.ritela.domain.DoodlePoint(.2f, .3f),
                        app.ritela.domain.DoodlePoint(.6f, .4f)
                    ),
                    0xffee839d,
                    .02f
                )
            ),
            "drawn"
        )
        val packet = message("packet", "peer", 1_700_000_000_000L).copy(
            delivered = true,
            body = org.json.JSONObject().put("note", "With love")
                .put("periods", org.json.JSONArray().put(org.json.JSONObject().put("start", 1)))
                .put("doodle", app.ritela.data.PartnerCodec.doodle(doodle)).toString()
        )
        compose.activity.setContent {
            HomeTheme { PartnerMessageBubble(packet, packet.received, {}, {}, true) }
        }
        compose.onNodeWithText("With love").assertIsDisplayed()
        compose.onNodeWithText("drawn").assertIsDisplayed()
        compose.onNodeWithTag("partner-attached-doodle").assertIsDisplayed()
    }

    @Test fun timelinePreservesMessagesAndUpdatesStatusesWithoutShowingAcknowledgements() {
        val original = message("one", "peer", 60000)
        val other = message("other", "other", 1000)
        val ack = original.copy(id = "ack", kind = "ACK")
        val opened = original.copy(id = "receipt", kind = "OPENED")
        val second = original.copy(id = "two", outgoing = false, received = 120000)
        val timeline =
            conversationMessages(listOf(second, opened, other, ack, original), setOf("peer"))
        assertEquals(listOf("one", "two"), timeline.map { it.id })
        assertFalse(timeline.first().delivered)
        val updated =
            conversationMessages(
                listOf(original.copy(delivered = true), second, ack),
                setOf("peer")
            )
        assertEquals(timeline.map { it.id }, updated.map { it.id })
        assertTrue(updated.first().delivered)
    }

    @Test fun deferredDoodleKeepsCaptionAndThumbnailHiddenUntilOpened() {
        ready()
        val now = mutableStateOf(100000L)
        val opened = mutableStateOf(false)
        var opens = 0
        val doodle = app.ritela.domain.Doodle(
            listOf(
                app.ritela.domain.DoodleStroke(
                    listOf(app.ritela.domain.DoodlePoint(.2f, .3f)),
                    0xFFDB8298,
                    .02f
                )
            ),
            "Secret surprise",
            app.ritela.domain.DoodleOpening.AFTER_DATE,
            200000L
        )
        val message = message("surprise", "peer", 100000).copy(
            kind = "DOODLE",
            outgoing = false,
            body = app.ritela.data.PartnerCodec.doodle(doodle).toString()
        )
        compose.activity.setContent {
            HomeTheme {
                PartnerMessageBubble(message.copy(opened = opened.value), now.value, {
                    opens++
                }, {}, true)
            }
        }
        compose.onNodeWithText("Secret surprise").assertDoesNotExist()
        compose.onNodeWithTag("partner-thumbnail-surprise").assertDoesNotExist()
        compose.onNodeWithText(
            compose.activity.getString(R.string.partner_open_doodle)
        ).assertDoesNotExist()
        compose.runOnIdle { now.value = 200001L }
        compose.onNodeWithText(
            compose.activity.getString(R.string.partner_open_doodle)
        ).performClick()
        assertEquals(1, opens)
        compose.onNodeWithText("Secret surprise").assertDoesNotExist()
        compose.runOnIdle { opened.value = true }
        compose.onNodeWithTag("partner-thumbnail-surprise").assertIsDisplayed()
        compose.onNodeWithText("Secret surprise").assertIsDisplayed()
    }

    @Test fun explicitPermissionExpansionPreservesOtherGrantsWithoutAddingSensitiveCategories() {
        val previous = ShareScope(setOf(ShareCategory.TAGS), tags = mapOf("mood" to setOf("happy")))
        val requested = ShareScope(setOf(ShareCategory.PERIODS, ShareCategory.FORECASTS))
        val result = PartnerSharing.union(previous, requested)
        assertTrue(result.permits(previous))
        assertTrue(result.permits(requested))
        assertFalse(result.permits(ShareScope(setOf(ShareCategory.SECTIONS), setOf("sex", "note"))))
    }

    @Test fun sharedViewKeepsIndividualSenderNames() {
        ready()
        val group = PartnerContact("group", "Together", setOf("peer", "other"), group = true)
        val incoming = listOf(
            message("first", "peer", 60000).copy(outgoing = false),
            message("second", "other", 120000).copy(outgoing = false)
        )
        compose.activity.setContent {
            HomeTheme {
                PartnerConversation(
                    group, incoming, "Not connected", false, false,
                    1791792000000, {}, {}, {}, {}, {},
                    senderNames = mapOf(
                        "peer" to "Margo",
                        "other" to "Alex"
                    )
                )
            }
        }
        compose.onNodeWithText("Margo").assertIsDisplayed()
        compose.onNodeWithText("Alex").assertIsDisplayed()
    }

    private fun message(id: String, identity: String, time: Long) = PartnerMessage(
        id, identity, "device", true, "DATA", time, time,
        "{\"periods\":[{\"start\":20730,\"end\":20734}]}", ""
    )

    private fun capture(name: String) {
        if (System.getProperty("ritela.skipScreenshots") == "true") return
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(requireNotNull(System.getProperty("ritela.screenshotDir"))).apply {
            mkdirs()
        }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
