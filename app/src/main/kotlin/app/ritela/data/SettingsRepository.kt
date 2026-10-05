package app.ritela.data

import android.content.SharedPreferences
import androidx.core.content.edit
import app.ritela.domain.PredictionDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SettingsRepository(private val preferences: SharedPreferences) {
    private val state = MutableStateFlow(
        PredictionDefaults(
            preferences.getInt("cycleLength", 28).coerceIn(1, 365),
            preferences.getInt("periodDuration", 5).coerceIn(1, 60)
        )
    )
    val values = state.asStateFlow()

    suspend fun save(value: PredictionDefaults) = withContext(Dispatchers.IO) {
        require(value.cycleLength in 1..365 && value.periodDuration in 1..60)
        preferences.edit(commit = true) {
            putInt("cycleLength", value.cycleLength)
            putInt("periodDuration", value.periodDuration)
        }
        state.value = value
    }
}
