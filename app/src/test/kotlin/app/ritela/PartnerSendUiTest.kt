package app.ritela

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import app.ritela.data.LocalPartnerKeys
import app.ritela.domain.Doodle
import app.ritela.domain.DoodlePoint
import app.ritela.domain.DoodleStroke
import app.ritela.ui.DoodleEditor
import app.ritela.ui.HomeTheme
import app.ritela.ui.LocalProfileSession
import app.ritela.ui.PartnerScreen
import app.ritela.ui.PartnerSendDialog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-rRU-w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PartnerSendUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val doodle =
        Doodle(listOf(DoodleStroke(listOf(DoodlePoint(.2f, .3f)), 0xffee839d, .02f)))

    @Test fun pendingMessageReopensTheSamePacketAndReturningDoesNotOpenIt() {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            compose.activity,
            app.ritela.data.RitelaDatabase::class.java
        ).build()
        val keys = LocalPartnerKeys.create()
        val repository = app.ritela.data.PartnerRepository(db, { keys }, {})
        val remote = LocalPartnerKeys.create()
        try {
            val message = runBlocking {
                confirmedPair(repository, remote, "Synthetic partner")
                repository.prepareDoodle(remote.identityId, remote.deviceId, doodle)
            }
            val visible = mutableStateOf(true)
            compose.activity.setContent {
                if (visible.value) {
                    val requests = androidx.compose.runtime.remember {
                        app.ritela.ui.PartnerSendRequests()
                    }
                    androidx.compose.runtime.SideEffect {
                        requests.bind("profile", "chat", remote.identityId)
                    }
                    androidx.compose.runtime.DisposableEffect(requests) {
                        onDispose { requests.invalidate() }
                    }
                    HomeTheme {
                        app.ritela.ui.PartnerMessageBubble(message, message.received, {}, {
                            val retry = runBlocking { repository.retry(message.id) }
                            requests.present(requests.request(), retry)
                        }, true)
                        requests.message?.let {
                            PartnerSendDialog(it, false, requests::dismiss, {}, {})
                        }
                    }
                }
            }
            repeat(2) {
                compose.onNodeWithTag("partner-send-${message.id}").performClick()
                compose.onNodeWithTag("partner-send-file").assertIsDisplayed()
                compose.onNodeWithTag("partner-send-close").performClick()
                compose.onNodeWithTag("partner-send-file").assertDoesNotExist()
            }
            compose.runOnIdle { visible.value = false }
            compose.runOnIdle { visible.value = true }
            compose.onNodeWithTag("partner-send-file").assertDoesNotExist()
            compose.onNodeWithTag("partner-send-${message.id}").assertIsDisplayed()
            assertEquals(false, runBlocking { repository.retry(message.id) }.delivered)
        } finally {
            db.close()
        }
    }

    @Test fun doodleAndTransportActionsHaveEqualHeightWithLargeRussianText() {
        compose.activity.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f)
            ) {
                HomeTheme { DoodleEditor({}, {}, {}) }
            }
        }
        equalBounds("doodle-save", "doodle-prepare")
        val message = app.ritela.data.PartnerMessage(
            "layout", "peer", "device", true, "DATA", 1, 1, "{}", ""
        )
        compose.activity.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f)
            ) {
                HomeTheme { PartnerSendDialog(message, false, {}, {}, {}) }
            }
        }
        equalBounds("partner-send-file", "partner-send-bluetooth")
        compose.onNodeWithText("♡").assertDoesNotExist()
    }

    private fun equalBounds(left: String, right: String) {
        compose.onNodeWithTag(left).assertIsDisplayed()
        compose.onNodeWithTag(right).assertIsDisplayed()
        val first = compose.onNodeWithTag(left).fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag(right).fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, .5f)
        assertEquals(first.height, second.height, .5f)
        assertEquals(first.bottom, second.bottom, .5f)
    }
}
