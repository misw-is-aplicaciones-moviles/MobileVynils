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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * One workflow state shared by the picker and its child date sheet.
 * Visible rows are built in [defaultDispatcher]. A newer search cancels the previous one, so the main thread
 * only draws the latest list.
 */
class PrizePickerViewModel(
    private val savedState: SavedStateHandle,
    private val repository: PrizeRepository,
    private val session: SessionRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
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
    private var filterJob: Job? = null
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
                        publishCatalog(result.value)
                    }
                }
            }
    }

    fun onQueryChanged(query: String) {
        if (current().isSubmitting) return
        savedState[QUERY] = query
        update { copy(query = query) }
        refreshVisiblePrizes()
    }

    fun onPrizeSelected(id: Int) {
        val state = current()
        if (!state.canEdit || state.isSubmitting || state.isUncertain) return
        if (id in state.assignedIds || id == state.selectedId) return
        val prize = state.prizes.firstOrNull { it.id == id } ?: return
        savedState[SELECTED_ID] = id
        savedState[DATE] = null
        update { copy(selectedId = id, selectedPrize = prize, date = null, submitError = null, dateRequired = false) }
        refreshVisiblePrizes()
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
                selectedPrize = prize,
                date = null,
            )
        }
        emit(PrizePickerEvent.PrizeCreated)
        refreshVisiblePrizes()
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
                    update {
                        copy(isSubmitting = false, assignedIds = assignedIds + prize.id, selectedPrize = null)
                    }
                    refreshVisiblePrizes()
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

    /** Rows and the resolved selection arrive together, so a render never filters the catalog. */
    private suspend fun publishCatalog(catalog: List<Prize>) {
        filterJob?.cancel()
        val query = current().query
        val selectedId = current().selectedId
        val created = createdPrize
        val snapshot =
            withContext(defaultDispatcher) {
                snapshotPrizeCatalog(catalog, created, assignmentIds, query, selectedId)
            }
        update {
            copy(
                prizes = snapshot.prizes,
                assignedIds = snapshot.assignedIds,
                visiblePrizes = snapshot.rows,
                selectedPrize = snapshot.selectedPrize,
                isLoading = false,
                loadError = null,
            )
        }
        // A search or a new prize that landed while the catalog was crossing is applied by the next build.
        if (current().query != query || current().selectedId != selectedId || createdPrize !== created) {
            refreshVisiblePrizes()
        }
    }

    /** Rebuilds the rows for the current search and selection. Only the latest call publishes. */
    private fun refreshVisiblePrizes() {
        filterJob?.cancel()
        val state = current()
        if (state.isLoading || state.prizes.isEmpty()) return
        val query = state.query
        val selectedId = state.selectedId
        val assignedIds = state.assignedIds
        val prizes = state.prizes
        val created = createdPrize
        filterJob =
            viewModelScope.launch {
                val prepared =
                    withContext(defaultDispatcher) {
                        val merged = includingCreated(prizes, created)
                        merged to prepareVisiblePrizes(merged, assignedIds, query, selectedId)
                    }
                // withContext checks cancellation on return: a result of an older search is never published.
                update {
                    copy(
                        prizes = prepared.first,
                        visiblePrizes = prepared.second.rows,
                        selectedPrize = prepared.second.selectedPrize,
                    )
                }
            }
    }

    /** Keeps a prize created while the catalog load was still crossing, and leaves an existing list untouched. */
    private fun includingCreated(
        prizes: List<Prize>,
        created: Prize?,
    ): List<Prize> = if (created == null || prizes.any { it.id == created.id }) prizes else listOf(created) + prizes

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
