package app.ritela

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.ritela.data.BackupCategory
import app.ritela.data.BackupSelection
import app.ritela.data.ProfileRegistry
import app.ritela.domain.DayLog
import app.ritela.ui.BackupChoices
import app.ritela.ui.HomeTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
class UserProfilesUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForHome() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("nav-today").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun addRenameSwitchAndLanguageAreIndependentAndReturnToHome() {
        val app = compose.activity.application as RitelaApplication
        val day = LocalDate.now().minusDays(3)
        runBlocking { app.periods.add(day, day) }
        waitForHome()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("profile-switch").performScrollTo().performClick()
        compose.onNodeWithTag("profile-rename-${ProfileRegistry.FIRST_ID}").performClick()
        compose.onNodeWithTag("profile-name").performTextReplacement("First")
        compose.onNodeWithTag("profile-name-save").performClick()
        compose.waitUntil(5000) { app.profiles.registry.profiles.value.first().name == "First" }
        compose.onNodeWithTag("profile-add").performClick()
        compose.onNodeWithTag("profile-name").performTextReplacement("Second")
        compose.onNodeWithTag("profile-name-save").performClick()
        compose.waitUntil(5000) { app.profiles.registry.profiles.value.size == 2 }
        val second = app.profiles.registry.profiles.value.last()
        assertEquals(ProfileRegistry.FIRST_ID, app.profiles.registry.activeId)
        compose.onNodeWithTag("profile-select-${second.id}").performClick()
        compose.waitUntil(10_000) { app.profiles.registry.activeId == second.id }
        waitForHome()
        compose.onNodeWithTag("profile-picker").assertDoesNotExist()
        compose.onNodeWithTag("nav-today").assertIsSelected()
        assertTrue(runBlocking { app.periods.periods.first().isEmpty() })
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("profile-language").performScrollTo().performClick()
        compose.onNodeWithTag("language-en").performClick()
        compose.waitUntil(5000) { app.settings.language.value == "en" }
        compose.onNodeWithTag("settings-heading").assertTextEquals("Settings").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("settings-heading").assertTextEquals("Settings").assertIsDisplayed()
        compose.onNodeWithTag("profile-switch").performScrollTo().performClick()
        compose.onNodeWithTag("profile-select-${ProfileRegistry.FIRST_ID}").performClick()
        compose.waitUntil(10_000) { app.profiles.registry.activeId == ProfileRegistry.FIRST_ID }
        waitForHome()
        assertEquals("system", app.settings.language.value)
        assertEquals(day, runBlocking { app.periods.periods.first().single().start })
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("settings-heading").assertTextEquals("Настройки").assertIsDisplayed()
    }

    @Test fun exportChoicesCanBeCancelledWithoutBlockingSwitching() {
        val app = compose.activity.application as RitelaApplication
        waitForHome()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("backup-export").performScrollTo().performClick()
        compose.onNodeWithTag("backup-choices").assertIsDisplayed()
        assertTrue(app.profiles.session.value.backupActive.value)
        compose.onNodeWithTag("backup-choice-all").performClick()
        compose.onNodeWithTag("backup-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("backup-choice-PERIODS").performClick()
        compose.onNodeWithTag("backup-confirm").assertIsEnabled().performClick()
        compose.onNodeWithTag("backup-password").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithTag("profile-switch").performScrollTo().assertIsEnabled()
        assertTrue(!app.profiles.session.value.backupActive.value)
    }

    @Test
    @Config(sdk = [35], qualifiers = "ru-rRU-w320dp-h640dp", fontScale = 2f)
    fun renameOnSmallScreenWithLargeTextKeepsSaveAccessible() {
        val app = compose.activity.application as RitelaApplication
        waitForHome()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("profile-switch").performScrollTo().performClick()
        compose.onNodeWithTag("profile-rename-${ProfileRegistry.FIRST_ID}").performClick()
        compose.onNodeWithTag("profile-name").performTextReplacement(" ")
        compose.onNodeWithTag("profile-name-save").assertIsNotEnabled()
        compose.onNodeWithTag("profile-name").performTextReplacement("Synthetic long profile name")
        compose.onNodeWithTag(
            "profile-name-save"
        ).performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(5000) {
            app.profiles.registry.profiles.value.single().name == "Synthetic long profile name"
        }
        compose.onNodeWithTag("profile-picker").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "en-rUS-w320dp-h640dp", fontScale = 2f)
    fun importShowsOnlyAvailableCategoriesAndSelectionIsExplicit() {
        var chosen: BackupSelection? = null
        compose.activity.setContent {
            HomeTheme {
                BackupChoices(
                    false,
                    "Synthetic user",
                    setOf(BackupCategory.PERIODS, BackupCategory.DAYS),
                    onDismiss = {},
                    onConfirm = { chosen = it }
                )
            }
        }
        compose.onNodeWithTag("backup-choice-SETTINGS").assertDoesNotExist()
        compose.onNodeWithTag("backup-choice-JOURNAL").assertDoesNotExist()
        compose.onNodeWithTag("backup-choice-all").performScrollTo().performClick()
        compose.onNodeWithTag("backup-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("backup-choice-DAYS").performScrollTo().performClick()
        compose.onNodeWithTag("backup-confirm").assertIsDisplayed().performClick()
        assertEquals(setOf(BackupCategory.DAYS), chosen?.categories)
    }

    @Test
    @Config(sdk = [35], qualifiers = "en-rUS-w390dp-h844dp")
    fun deleteIsRightOfEditAndCancellationKeepsUserThenConfirmationRemovesOnlyThatUser() {
        val app = compose.activity.application as RitelaApplication
        val second = app.profiles.registry.add("Second")
        waitForHome()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("profile-switch").performScrollTo().performClick()
        val edit = compose.onNodeWithTag("profile-rename-${second.id}").performScrollTo()
            .fetchSemanticsNode().boundsInRoot
        val delete = compose.onNodeWithTag("profile-delete-${second.id}").performScrollTo()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(delete.left >= edit.right)
        compose.onNodeWithTag(
            "profile-delete-${second.id}"
        ).assertTextEquals("Delete").performClick()
        compose.onNodeWithTag("profile-delete-dialog").assertIsDisplayed()
        compose.onNodeWithText("Delete user?").assertIsDisplayed()
        compose.onNodeWithTag("profile-delete-cancel").performClick()
        assertEquals(2, app.profiles.registry.profiles.value.size)
        compose.onNodeWithTag("profile-delete-${second.id}").performClick()
        compose.onNodeWithTag("profile-delete-confirm").performClick()
        compose.waitUntil(5000) {
            app.profiles.registry.profiles.value.size == 1 &&
                compose.onAllNodesWithTag("profile-picker").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("profile-picker").assertIsDisplayed()
        compose.onNodeWithTag("profile-delete-${second.id}").assertDoesNotExist()
        assertEquals(ProfileRegistry.FIRST_ID, app.profiles.registry.activeId)
    }

    @Test
    @Config(sdk = [35], qualifiers = "ru-rRU-w320dp-h640dp", fontScale = 2f)
    fun deletingLastUserAtLargeTextOpensFreshHomeWithDefaultName() {
        val app = compose.activity.application as RitelaApplication
        runBlocking { app.days.save(DayLog(LocalDate.now(), note = "synthetic removal")) }
        waitForHome()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("profile-switch").performScrollTo().performClick()
        compose.onNodeWithTag(
            "profile-delete-${ProfileRegistry.FIRST_ID}"
        ).performScrollTo().performClick()
        compose.onNodeWithText("Удалить пользователя?").assertIsDisplayed()
        compose.onNodeWithTag("profile-delete-confirm").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { app.profiles.registry.activeId != ProfileRegistry.FIRST_ID }
        waitForHome()
        compose.onNodeWithTag("nav-today").assertIsSelected()
        assertEquals("qwerty", app.profiles.registry.profiles.value.single().name)
        assertTrue(runBlocking { app.days.logs.first().isEmpty() })
    }
}
