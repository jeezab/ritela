package app.ritela

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import app.ritela.data.PartnerCodec
import app.ritela.data.PartnerMessage
import app.ritela.domain.Doodle
import app.ritela.domain.DoodlePoint
import app.ritela.domain.DoodleStroke
import app.ritela.ui.HomeTheme
import app.ritela.ui.PartnerMessageBubble
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-rRU-w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PartnerMessageDeleteUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val data = PartnerMessage(
        "data", "peer", "phone", false, "DATA", 1, 1,
        """{"periods":[{"start":1,"end":5}]}""", "synthetic"
    )

    @Test fun longPressDefaultsToKeepingDataAndCancelDoesNotRememberTheCheckbox() {
        val removed = mutableStateOf(false)
        var erased: Boolean? = null
        compose.activity.setContent {
            HomeTheme {
                if (!removed.value) {
                    PartnerMessageBubble(data, 1, {}, {}, true, delete = {
                        erased = it
                        removed.value = true
                    })
                }
            }
        }
        longPress("data")
        compose.onNodeWithTag("partner-message-erase").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("partner-message-delete").performClick()
        compose.onNodeWithText(
            compose.activity.getString(R.string.partner_message_delete_data)
        ).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
        longPress("data")
        compose.onNodeWithTag("partner-message-erase").assertIsOff()
        compose.onNodeWithTag("partner-message-delete").performClick()
        compose.onNodeWithText(
            compose.activity.getString(R.string.partner_message_hide)
        ).assertExists()
        compose.onNodeWithTag("partner-message-delete-confirm").performClick()
        assertEquals(false, erased)
        compose.onNodeWithTag("partner-message-data").assertDoesNotExist()
    }

    @Test fun checkedDeletionRequiresItsOwnConfirmationAndSupportsAccessibleLongClick() {
        var erased: Boolean? = null
        compose.activity.setContent {
            HomeTheme { PartnerMessageBubble(data, 1, {}, {}, true, delete = { erased = it }) }
        }
        compose.onNodeWithTag(
            "partner-message-actions-data"
        ).performSemanticsAction(SemanticsActions.OnLongClick) {
            it()
        }
        compose.onNodeWithTag("partner-message-erase").assertIsOff().performClick()
        compose.onNodeWithTag("partner-message-delete").performClick()
        assertEquals(null, erased)
        compose.onNodeWithTag("partner-message-delete-confirm").performClick()
        assertEquals(true, erased)
    }

    @Test fun outgoingMessagesAndDoodlesDoNotOfferTheKeepDataCheckbox() {
        val doodle = Doodle(listOf(DoodleStroke(listOf(DoodlePoint(.2f, .3f)), 0xffee839d, .02f)))
        val chosen = mutableStateOf(data.copy(outgoing = true))
        var deleted = 0
        compose.activity.setContent {
            HomeTheme {
                PartnerMessageBubble(chosen.value, 1, {}, {}, true, delete = { deleted++ })
            }
        }
        listOf(
            data.copy(outgoing = true),
            data.copy(kind = "DOODLE", body = PartnerCodec.doodle(doodle).toString()),
            data.copy(
                kind = "DOODLE",
                outgoing = true,
                body = PartnerCodec.doodle(doodle).toString()
            )
        ).forEach { message ->
            compose.runOnIdle { chosen.value = message }
            longPress("data")
            compose.onNodeWithTag("partner-message-erase").assertDoesNotExist()
            compose.onNodeWithTag("partner-message-delete").performClick()
            compose.onNodeWithText(
                compose.activity.getString(R.string.partner_message_delete_contents)
            ).assertExists()
            compose.onNodeWithTag("partner-message-delete-confirm").performClick()
        }
        assertEquals(3, deleted)
    }

    private fun longPress(id: String) {
        compose.onNodeWithTag("partner-message-actions-$id").performTouchInput {
            longClick(Offset(10f, 10f))
        }
    }
}
