package app.ritela.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.ritela.RitelaApplication
import app.ritela.data.PeriodRepository
import app.ritela.domain.Period
import app.ritela.domain.PeriodProblem
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PeriodUiState(
    val periods: List<Period> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val problem: PeriodProblem? = null
)

class PeriodViewModel(private val repository: PeriodRepository) : ViewModel() {
    private val state = MutableStateFlow(PeriodUiState())
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.periods.collect { periods ->
                    state.update { it.copy(periods = periods, loading = false) }
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

    companion object {
        val Factory = viewModelFactory {
            initializer { PeriodViewModel((this[APPLICATION_KEY] as RitelaApplication).periods) }
        }
    }
}
