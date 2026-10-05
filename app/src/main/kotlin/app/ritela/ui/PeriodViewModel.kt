package app.ritela.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.ritela.RitelaApplication
import app.ritela.data.DayLogRepository
import app.ritela.data.PeriodRepository
import app.ritela.data.SettingsRepository
import app.ritela.data.ThemeMode
import app.ritela.domain.CycleAnalysis
import app.ritela.domain.DayLog
import app.ritela.domain.Period
import app.ritela.domain.PeriodProblem
import app.ritela.domain.PredictionDefaults
import app.ritela.domain.analyzeCycles
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PeriodUiState(
    val periods: List<Period> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val problem: PeriodProblem? = null,
    val today: LocalDate = LocalDate.now(),
    val analysis: CycleAnalysis = CycleAnalysis(),
    val defaults: PredictionDefaults = PredictionDefaults(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dayLogs: List<DayLog> = emptyList()
)

class PeriodViewModel(
    private val repository: PeriodRepository,
    private val settings: SettingsRepository? = null,
    private val days: DayLogRepository? = null
) : ViewModel() {
    private val state = MutableStateFlow(
        PeriodUiState(
            today = repository.today,
            themeMode =
                settings?.theme?.value ?: ThemeMode.SYSTEM
        )
    )
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                combine(
                    repository.periods,
                    settings?.values ?: flowOf(PredictionDefaults()),
                    settings?.theme ?: flowOf(ThemeMode.SYSTEM),
                    days?.logs ?: flowOf(emptyList())
                ) { periods, defaults, theme, logs ->
                    PeriodUiState(
                        periods = periods,
                        defaults = defaults,
                        themeMode = theme,
                        dayLogs = logs
                    )
                }.collect { source ->
                    val periods = source.periods
                    val defaults = source.defaults
                    val today = repository.today
                    state.update {
                        it.copy(
                            periods = periods,
                            loading = false,
                            today = today,
                            analysis = analyzeCycles(periods, today, defaults),
                            defaults = defaults,
                            themeMode = source.themeMode,
                            dayLogs = source.dayLogs
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.update { it.copy(loading = false, problem = PeriodProblem.STORAGE) }
            }
        }
    }

    fun save(start: LocalDate, end: LocalDate?) {
        persist { repository.add(start, end) }
    }

    fun finish(id: UUID, end: LocalDate) {
        persist { repository.finish(id, end) }
    }

    fun edit(id: UUID, start: LocalDate, end: LocalDate?) {
        persist { repository.edit(id, start, end) }
    }

    fun delete(id: UUID) {
        persist { repository.delete(id) }
    }

    private fun persist(operation: suspend () -> PeriodProblem?) {
        if (state.value.saving) return
        state.update { it.copy(saving = true, problem = null, saved = false) }
        viewModelScope.launch {
            val problem = try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PeriodProblem.STORAGE
            }
            state.update { it.copy(saving = false, saved = problem == null, problem = problem) }
        }
    }

    fun clearResult() {
        state.update { it.copy(saved = false, problem = null) }
    }

    fun refreshToday() {
        val today = repository.today
        state.update {
            it.copy(today = today, analysis = analyzeCycles(it.periods, today, it.defaults))
        }
    }

    fun setTheme(value: ThemeMode) {
        persist {
            settings?.saveTheme(value)
            null
        }
    }

    fun saveDay(log: DayLog) {
        persist { days?.save(log) ?: if (days == null) PeriodProblem.STORAGE else null }
    }

    fun updateDefaults(value: PredictionDefaults) {
        persist {
            settings?.save(value)
            null
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RitelaApplication
                PeriodViewModel(app.periods, app.settings, app.days)
            }
        }
    }
}
