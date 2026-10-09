package app.ritela.data

import android.content.SharedPreferences
import androidx.core.content.edit
import app.ritela.domain.PredictionDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SettingsRepository(
    private val preferences: SharedPreferences? = null,
    private val dao: ProfileSettingsDao? = null,
    initialLanguage: String = "system"
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loaded = MutableStateFlow(dao == null)
    val ready = loaded.asStateFlow()
    private val selectedLanguage = MutableStateFlow(initialLanguage)
    val language = selectedLanguage.asStateFlow()
    private val failure = MutableStateFlow(false)
    val failed = failure.asStateFlow()

    // Old preferences remain on disk for compatibility, but no longer control forecasts.
    private val state = MutableStateFlow(PredictionDefaults())
    val values = state.asStateFlow()
    private val appearance = MutableStateFlow(
        ThemeMode.entries.firstOrNull {
            it.name == preferences?.getString("themeMode", null)
        } ?: ThemeMode.SYSTEM
    )
    val theme = appearance.asStateFlow()

    init {
        if (dao != null) {
            scope.launch {
                try {
                    dao.seed(
                        ProfileSettings(language = initialLanguage, theme = appearance.value.name)
                    )
                    dao.observe().collect { value ->
                        val settings = requireNotNull(value)
                        require(settings.valid())
                        selectedLanguage.value = settings.language
                        appearance.value = ThemeMode.valueOf(settings.theme)
                        loaded.value = true
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failure.value = true
                }
            }
        }
    }

    suspend fun awaitReady() {
        kotlinx.coroutines.flow.combine(ready, failed) { ready, failed -> ready || failed }
            .first { it }
        check(!failed.value)
    }

    suspend fun saveLanguage(value: String) = withContext(Dispatchers.IO) {
        require(value in listOf("system", "ru", "en"))
        awaitReady()
        requireNotNull(dao).setLanguage(value)
    }

    fun close() {
        scope.cancel()
    }

    suspend fun saveTheme(value: ThemeMode) = withContext(Dispatchers.IO) {
        if (dao != null) {
            awaitReady()
            dao.setTheme(value.name)
        } else {
            requireNotNull(preferences).edit(commit = true) { putString("themeMode", value.name) }
        }
        appearance.value = value
    }

    suspend fun save(value: PredictionDefaults) = withContext(Dispatchers.IO) {
        require(value.cycleLength in 1..365 && value.periodDuration in 1..60)
        preferences?.edit(commit = true) {
            putInt("cycleLength", value.cycleLength)
            putInt("forecastPeriodDuration", value.periodDuration)
        }
        state.value = PredictionDefaults()
    }
}
