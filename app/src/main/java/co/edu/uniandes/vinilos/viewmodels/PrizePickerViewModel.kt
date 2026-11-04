package co.edu.uniandes.vinilos.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.Prize
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.repositories.PrizeRepository
import co.edu.uniandes.vinilos.repositories.SessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** One workflow state shared by the picker and its child date sheet. */
class PrizePickerViewModel(
    private val savedState: SavedStateHandle,
    private val repository: PrizeRepository,
    private val session: SessionRepository,
) : ViewModel() {
    val performerId: Int = savedState[PrizeFlowContract.PERFORMER_ID] ?: 0
    val performerName: String = savedState[PrizeFlowContract.PERFORMER_NAME] ?: ""
    val performerType: PerformerType? =
        PerformerType.entries.firstOrNull { it.name == savedState.get<String>(PrizeFlowContract.PERFORMER_TYPE) }
    private val assignmentIds = savedState.get<IntArray>(PrizeFlowContract.ASSIGNMENT_IDS)?.toSet().orEmpty()
    private val validArtist = performerId > 0 && performerName.isNotBlank() && performerType != null
    private val _events = MutableLiveData<Event<PrizePickerEvent>>()
    val events: LiveData<Event<PrizePickerEvent>> = _events
    private val _uiState =
        MediatorLiveData(
            PrizePickerUiState(
                selectedId = savedState[SELECTED_ID],
                query = savedState[QUERY] ?: "",
                date = restoreDate(savedState[DATE]),
                isUncertain = savedState[WRITE_PENDING] ?: false,
                canEdit = validArtist && session.role.value == UserRole.COLLECTOR,
            ),
        )
    val uiState: LiveData<PrizePickerUiState> = _uiState
    private var loadJob: Job? = null
    private var createdPrize: Prize? = null

    init {
        _uiState.addSource(session.role) { role ->
            update { copy(canEdit = validArtist && role == UserRole.COLLECTOR) }
            if (role != UserRole.COLLECTOR) {
                loadJob?.cancel()
                emit(PrizePickerEvent.OpenRoleSelector)
            }
        }
        if (current().canEdit) load(forceRefresh = true) else emit(PrizePickerEvent.Close)
    }

    fun load(forceRefresh: Boolean = false) {
        if (!current().canEdit || loadJob?.isActive == true || current().isSubmitting) return
        update { copy(isLoading = true, loadError = null) }
        loadJob =
            viewModelScope.launch {
                when (val result = repository.getPrizes(forceRefresh)) {
                    is DataResult.Failure -> {
                        update { copy(isLoading = false, loadError = result.error) }
                    }

                    is DataResult.Success -> {
                        val prizes =
                            createdPrize?.let { new -> listOf(new) + result.value.filter { it.id != new.id } }
                                ?: result.value
                        val assigned =
                            prizes
                                .filter { p ->
                                    p.assignments.any { it.id in assignmentIds }
                                }.map { it.id }
                                .toSet()
                        update { copy(prizes = prizes, assignedIds = assigned, isLoading = false, loadError = null) }
                    }
                }
            }
    }

    fun onQueryChanged(query: String) {
        if (current().isSubmitting) return
        savedState[QUERY] = query
        update { copy(query = query) }
    }

    fun onPrizeSelected(id: Int) {
        val state = current()
        if (!state.canEdit || state.isSubmitting || state.isUncertain) return
        if (id in state.assignedIds || id == state.selectedId || state.prizes.none { it.id == id }) return
        savedState[SELECTED_ID] = id
        savedState[DATE] = null
        update { copy(selectedId = id, date = null, submitError = null, dateRequired = false) }
    }

    fun onContinue() {
        if (current().isUncertain) {
            onReviewArtist()
            return
        }
        if (current().canContinue) emit(PrizePickerEvent.OpenDate)
    }

    fun onDateSelected(date: LocalDate) {
        if (current().isSubmitting || current().isUncertain) return
        savedState[DATE] = date.toString()
        update { copy(date = date, dateRequired = false, submitError = null) }
    }

    fun onCreatePrize() {
        if (current().canEdit && !current().isSubmitting && !current().isUncertain) emit(PrizePickerEvent.OpenCreate)
    }

    fun onPrizeCreated(prize: Prize) {
        if (!current().canEdit || prize.id <= 0) return
        createdPrize = prize
        savedState[QUERY] = ""
        savedState[SELECTED_ID] = prize.id
        savedState[DATE] = null
        update {
            copy(
                prizes = listOf(prize) + prizes.filter { it.id != prize.id },
                query = "",
                selectedId = prize.id,
                date = null,
            )
        }
        emit(PrizePickerEvent.PrizeCreated)
    }

    fun onSave() {
        val state = current()
        if (!state.canContinue || session.role.value != UserRole.COLLECTOR) return
        val date = state.date
        if (date == null) {
            update { copy(dateRequired = true) }
            return
        }
        val prize = state.selectedPrize
        val type = performerType
        if (prize == null || type == null) return
        savedState[WRITE_PENDING] = true
        update { copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            when (val result = repository.assignPrize(performerId, type, prize.id, date)) {
                is DataResult.Success -> {
                    savedState[WRITE_PENDING] = false
                    update { copy(isSubmitting = false, assignedIds = assignedIds + prize.id) }
                    emit(PrizePickerEvent.Assigned(result.value))
                }

                is DataResult.Failure -> {
                    val definiteFailure = (result.error as? DataError.Http)?.code in DEFINITE_FAILURES
                    savedState[WRITE_PENDING] = !definiteFailure
                    update { copy(isSubmitting = false, submitError = result.error, isUncertain = !definiteFailure) }
                }
            }
        }
    }

    fun onReviewArtist() {
        if (current().isUncertain && !current().isSubmitting) emit(PrizePickerEvent.ReviewArtist)
    }

    fun onRoleChipClicked() {
        if (!current().isSubmitting) session.clearRole()
    }

    private fun current() = checkNotNull(_uiState.value)

    private fun update(change: PrizePickerUiState.() -> PrizePickerUiState) {
        _uiState.value = current().change()
    }

    private fun emit(event: PrizePickerEvent) {
        _events.value = Event(event)
    }

    companion object {
        private const val SELECTED_ID = "selectedPrizeId"
        private const val QUERY = "prizeQuery"
        private const val DATE = "selectedPremiationDate"
        private const val WRITE_PENDING = "prizeWritePending"
        private val DEFINITE_FAILURES = setOf(400, 404)

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VinilosApplication
                    PrizePickerViewModel(
                        createSavedStateHandle(),
                        app.container.prizeRepository,
                        app.container.sessionRepository,
                    )
                }
            }

        private fun restoreDate(value: String?): LocalDate? =
            try {
                value?.let(LocalDate::parse)
            } catch (_: DateTimeParseException) {
                null
            }
    }
}
