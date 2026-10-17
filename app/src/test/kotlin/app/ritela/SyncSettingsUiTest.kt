package app.ritela

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import app.ritela.data.RitelaDatabase
import app.ritela.data.sync.SyncSettingsRepository
import app.ritela.ui.HomeTheme
import app.ritela.ui.SyncSettingsDialog
import java.util.UUID
import kotlinx.coroutines.runBlocking
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
class SyncSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready() {
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("nav-today").fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun database(action: (RitelaDatabase) -> Unit) {
        val name = "sync-ui-${UUID.randomUUID()}.db"
        val db = syncTestDatabase(compose.activity, name)
        try {
            action(db)
        } finally {
            db.close()
            compose.activity.deleteDatabase(name)
        }
    }

    @Test fun settingsOpensManualModeWithoutConsentAndCloseReturnsToSettings() {
        ready()
        val app = compose.activity.application as RitelaApplication
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("sync-settings").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("sync-queue-empty").fetchSemanticsNodes().isNotEmpty()
        }
        val internet = compose.onNodeWithTag("sync-internet-unavailable")
        if (BuildConfig.RELAY_ORIGIN.isEmpty()) {
            internet.assertIsNotEnabled()
        } else {
            internet.assertIsEnabled()
        }
        compose.onNodeWithTag("sync-queue-empty").performScrollTo().assertIsDisplayed()
        assertFalse(
            runBlocking {
                app.profiles.session.value.syncSettings.read().internetRequested
            }
        )
        compose.onNodeWithTag("sync-close").performScrollTo().performClick()
        compose.onNodeWithTag("sync-settings-dialog").assertDoesNotExist()
        compose.onNodeWithTag("settings-heading").assertTextEquals("Настройки")
    }

    @Test fun openingAndCancellingPilotConsentCannotRegisterOrCreateRelayKeys() {
        ready()
        database { db ->
            val context = compose.activity
            val profile = UUID.randomUUID().toString()
            val wrapping = javax.crypto.spec.SecretKeySpec(ByteArray(32) { 3 }, "AES")
            val vault = app.ritela.data.sync.RelayKeyVault(context, profile) { wrapping }
            val local = app.ritela.data.LocalPartnerKeys.create()
            val partner = app.ritela.data.PartnerRepository(db, { local }, {})
            val calls = java.util.concurrent.atomic.AtomicInteger()
            val exchange = app.ritela.data.sync.RelayHttpExchange { request, authorize ->
                authorize()
                calls.incrementAndGet()
                val body = if (request.method == "POST") {
                    org.json.JSONObject().put("handle", request.headers.getValue("X-Ritela-Device"))
                } else {
                    org.json.JSONObject().put("packets", org.json.JSONArray()).put("cursor", "0")
                }
                app.ritela.data.sync.RelayHttpResponse(200, body.toString().toByteArray())
            }
            val runtime = app.ritela.data.sync.RelayRuntime(
                context,
                profile,
                db,
                { partner },
                { true },
                "https://relay.example",
                vault,
                exchange
            )
            val repository = SyncSettingsRepository(db, { runtime })
            compose.activity.setContent {
                HomeTheme { SyncSettingsDialog(repository) {} }
            }
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("sync-queue-empty").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("sync-internet-unavailable").performScrollTo().performClick()
            compose.onNodeWithTag("sync-activate-confirm").assertIsEnabled()
            compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
            assertEquals(0, calls.get())
            assertEquals(null, vault.load())
            assertFalse(runBlocking { repository.read().internetRequested })
            compose.onNodeWithTag("sync-internet-unavailable").performScrollTo().performClick()
            compose.onNodeWithTag("sync-activation-token").assertDoesNotExist()
            compose.onNodeWithTag("sync-activate-confirm").performClick()
            compose.waitUntil(10000) { runBlocking { repository.read().registered } }
            compose.waitUntil(10000) { calls.get() == 2 }
            assertTrue(runBlocking { repository.read().internetRequested })
            compose.activity.setContent {}
            compose.waitForIdle()
            runBlocking { runtime.close() }
            java.io.File(context.filesDir, "relay-$profile.keys").delete()
        }
    }

    @Test fun contentsNeverExposeTextAndCancellationRequiresConfirmation() {
        ready()
        database { db ->
            runBlocking { seedSyncSettings(db) }
            compose.activity.setContent {
                HomeTheme { SyncSettingsDialog(SyncSettingsRepository(db)) {} }
            }
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("sync-entry-m:message").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("sync-contents-m:message").performScrollTo().performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("sync-details-close").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Текст послания").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("PRIVATE_SENTINEL").assertDoesNotExist()
            compose.onNodeWithTag("sync-details-close").performClick()
            compose.onNodeWithTag("sync-cancel-m:message").performScrollTo().performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
            assertEquals(
                "PENDING_WRAP",
                runBlocking {
                    db.syncMessageIntents().get("message")!!.state
                }
            )
            compose.onNodeWithTag("sync-cancel-m:message").performScrollTo().performClick()
            compose.onNodeWithTag("sync-confirm-action").performClick()
            compose.waitUntil(5000) {
                runBlocking { db.syncMessageIntents().get("message")!!.state == "CANCELLED" }
            }
            assertEquals(
                "PRIVATE_SENTINEL",
                org.json.JSONObject(
                    runBlocking {
                        db.partner().message("message")!!.body
                    }
                ).getString("note")
            )
            assertFalse(runBlocking { db.partner().message("message")!!.delivered })
            compose.activity.setContent {}
            compose.waitForIdle()
        }
    }

    @Test fun disableNeedsConfirmationAndDoesNotClaimRemoteDeletion() {
        ready()
        database { db ->
            runBlocking { seedSyncSettings(db) }
            compose.activity.setContent {
                HomeTheme { SyncSettingsDialog(SyncSettingsRepository(db)) {} }
            }
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("sync-disable").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("sync-disable").performScrollTo().performClick()
            compose.onNodeWithText(
                compose.activity.getString(R.string.sync_disable_confirmation)
            ).assertIsDisplayed()
            assertTrue(runBlocking { SyncSettingsRepository(db).read().internetRequested })
            compose.onNodeWithTag("sync-confirm-action").performClick()
            compose.waitUntil(5000) {
                !runBlocking { SyncSettingsRepository(db).read().internetRequested }
            }
            assertEquals("STORED", runBlocking { db.syncQueue().packet("record-1")!!.state })
            compose.activity.setContent {}
            compose.waitForIdle()
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w320dp-h640dp")
    fun englishNarrowScreenAtDoubleFontKeepsActionsAndPrivacyReachable() {
        ready()
        database { db ->
            runBlocking { seedSyncSettings(db) }
            compose.activity.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, 2f)
                ) {
                    HomeTheme { SyncSettingsDialog(SyncSettingsRepository(db)) {} }
                }
            }
            compose.onNodeWithTag(
                "sync-internet-unavailable"
            ).performScrollTo().assertIsNotEnabled()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("sync-disable").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("sync-disable").performScrollTo().performClick()
            compose.onNodeWithTag("sync-confirm-action").assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
            assertTrue(runBlocking { SyncSettingsRepository(db).read().internetRequested })
            compose.onNodeWithText("Access and deletion").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.sync_privacy_local))
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("PRIVATE_SENTINEL").assertDoesNotExist()
            compose.activity.setContent {}
            compose.waitForIdle()
        }
    }
}
