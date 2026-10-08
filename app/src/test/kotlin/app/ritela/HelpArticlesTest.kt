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
}
