package app.ritela

import android.content.res.Configuration
import app.ritela.domain.DayLog
import app.ritela.domain.Pain
import app.ritela.domain.analyzeCycles
import app.ritela.ui.HelpLibrary
import app.ritela.ui.relevantArticles
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HelpArticlesTest {
    @Test
    fun educationalArticlesAreAvailableOfflineInBothLanguages() {
        val context = RuntimeEnvironment.getApplication()
        val ids = setOf("cycle", "menstruation", "follicular", "ovulation", "luteal")
        val articles = HelpLibrary.filter { it.id in ids }
        assertEquals(ids, articles.map { it.id }.toSet())
        for (article in articles) {
            val bodies = listOf("en", "ru").map { language ->
                val configuration = Configuration(context.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language))
                }
                val localized = context.createConfigurationContext(configuration)
                assertTrue(localized.getString(article.title).isNotBlank())
                localized.getString(article.body).also {
                    assertTrue(it.contains('\n'))
                    assertTrue(it.length > 100)
                }
            }
            assertTrue(bodies[0] != bodies[1])
        }
    }

    @Test
    fun educationDoesNotDisplaceUrgentSymptomAdvice() {
        val today = LocalDate.of(2026, 10, 8)
        val analysis = analyzeCycles(emptyList(), today)
        assertEquals("cycle", relevantArticles(null, analysis).first().id)
        val log = DayLog(date = today, headache = Pain.SEVERE, cramps = Pain.SEVERE)
        assertEquals(
            listOf("headache-urgent", "pain-urgent"),
            relevantArticles(log, analysis).take(2).map { it.id }
        )
    }

    @Test fun recentRecordsHaveDecayingWeightsAndBothListsShareTheSameOrder() {
        val today = LocalDate.of(2026, 10, 11)
        val analysis = analyzeCycles(emptyList(), today)
        val fresh = DayLog(today.minusDays(1), cramps = Pain.MILD)
        val old = fresh.copy(date = today.minusDays(12))
        val freshWeights = app.ritela.ui.articleWeights(listOf(fresh), analysis, today)
        val oldWeights = app.ritela.ui.articleWeights(listOf(old), analysis, today)
        assertTrue(freshWeights.getValue("movement") > oldWeights.getValue("movement"))
        val ranking = app.ritela.ui.rankedArticles(listOf(fresh), analysis, today)
        assertEquals(HelpLibrary.size, ranking.size)
        assertEquals(HelpLibrary.map { it.id }.toSet(), ranking.map { it.id }.toSet())
        assertEquals(listOf("warmth", "movement", "pain-care"), ranking.take(3).map { it.id })
        assertEquals(
            "cycle",
            app.ritela.ui.rankedArticles(
                listOf(old.copy(date = today.minusDays(14))),
                analysis,
                today
            ).first().id
        )
        assertEquals(
            "cycle",
            app.ritela.ui.rankedArticles(
                listOf(fresh.copy(date = today.plusDays(1))),
                analysis,
                today
            ).first().id
        )
    }

    @Test fun severeTodayOutranksRepeatedHistoryAndExpiredSexDoesNotTriggerEmergencyArticle() {
        val today = LocalDate.of(2026, 10, 11)
        val analysis = analyzeCycles(emptyList(), today)
        val history = (1L..13).map { DayLog(today.minusDays(it), cramps = Pain.MILD) }
        val urgent = DayLog(today, headache = Pain.SEVERE, cramps = Pain.SEVERE)
        assertEquals(
            listOf("headache-urgent", "pain-urgent"),
            app.ritela.ui.rankedArticles(history + urgent, analysis, today).take(2).map { it.id }
        )
        assertEquals(
            0.0,
            app.ritela.ui.articleWeights(
                listOf(DayLog(today.minusDays(5), sex = setOf(app.ritela.domain.Sex.NO_BARRIER))),
                analysis,
                today
            ).getValue("emergency"),
            0.0
        )
        assertEquals(
            0.0,
            app.ritela.ui.articleWeights(
                listOf(DayLog(today, custom = mapOf("headache" to setOf("custom-severe")))),
                analysis,
                today
            ).getValue("headache-urgent"),
            0.0
        )
    }
}
