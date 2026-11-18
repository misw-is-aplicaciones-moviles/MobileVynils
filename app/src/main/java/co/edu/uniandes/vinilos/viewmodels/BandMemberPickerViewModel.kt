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
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.repositories.PerformerRepository
import co.edu.uniandes.vinilos.repositories.SessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Chooses a musician for a band and adds it. The list is the artist list revalidated against the backend, so each
 * musician carries its current band. A musician of another band is blocked here, because the backend would move it
 * instead of rejecting it. Rows are built in [defaultDispatcher], once per search or selection, and a newer search
 * cancels the previous one.
 */
class BandMemberPickerViewModel(
    private val savedState: SavedStateHandle,
    private val repository: PerformerRepository,
    private val session: SessionRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val bandId: Int = savedState[BandMemberFlowContract.BAND_ID] ?: 0
    val bandName: String = savedState[BandMemberFlowContract.BAND_NAME] ?: ""
    private val initialMemberIds = savedState.get<IntArray>(BandMemberFlowContract.MEMBER_IDS)?.toSet().orEmpty()
    private val validBand = bandId > 0 && bandName.isNotBlank()

    private val _uiState =
        MediatorLiveData(
            BandMemberPickerUiState(
                bandId = bandId,
                bandName = bandName,
                memberIds = initialMemberIds,
                query = savedState[QUERY] ?: "",
                selectedId = savedState[SELECTED_ID],
                isUncertain = savedState[WRITE_PENDING] ?: false,
                canEdit = validBand && session.role.value == UserRole.COLLECTOR,
            ),
        )
    val uiState: LiveData<BandMemberPickerUiState> = _uiState
    private val _events = MutableLiveData<Event<BandMemberPickerEvent>>()
    val events: LiveData<Event<BandMemberPickerEvent>> = _events
    private var loadJob: Job? = null
    private var filterJob: Job? = null

    init {
        _uiState.addSource(session.role) { role ->
            val wasCollector = current().canEdit
            update { copy(canEdit = validBand && role == UserRole.COLLECTOR) }
            if (wasCollector && role != UserRole.COLLECTOR) {
                loadJob?.cancel()
                emit(BandMemberPickerEvent.OpenRoleSelector)
            }
        }
        when {
            !validBand -> emit(BandMemberPickerEvent.Close)
            !current().canEdit -> emit(BandMemberPickerEvent.OpenRoleSelector)
            else -> load()
        }
    }

    fun retry() = load()

    fun onQueryChanged(query: String) {
        if (current().isSubmitting || query == current().query) return
        savedState[QUERY] = query
        update { copy(query = query) }
        refreshVisibleRows()
    }

    fun onMusicianSelected(id: Int) {
        val state = current()
        if (!state.canEdit || state.isSubmitting || state.isUncertain) return
        if (id == state.selectedId || id in state.memberIds || state.musicians.none { it.id == id }) return
        savedState[SELECTED_ID] = id
        update { copy(selectedId = id, conflict = null, submitError = null) }
        refreshVisibleRows()
    }

    fun onAdd() {
        val state = current()
        if (state.isUncertain) {
            if (!state.isSubmitting) emit(BandMemberPickerEvent.ReviewBand)
            return
        }
        val musician = state.selectedMusician
        if (!state.canAdd || musician == null || session.role.value != UserRole.COLLECTOR) return
        if (musician.hasOtherBand(bandId)) {
            // The backend would take the musician out of its current band, so nothing is sent.
            update { copy(conflict = BandConflict(musician.name, musician.bandName), submitError = null) }
            return
        }
        savedState[WRITE_PENDING] = true
        update { copy(isSubmitting = true, submitError = null, conflict = null) }
        viewModelScope.launch {
            when (val result = repository.addMusicianToBand(bandId, musician.id)) {
                is DataResult.Success -> {
                    savedState[WRITE_PENDING] = false
                    update { copy(isSubmitting = false, memberIds = memberIds + musician.id) }
                    emit(BandMemberPickerEvent.Added(result.value))
                }

                is DataResult.Failure -> {
                    // 400 and 404 mean nothing was saved; any other failure may have reached the backend.
                    val definite = (result.error as? DataError.Http)?.code in DEFINITE_FAILURES
                    savedState[WRITE_PENDING] = !definite
                    update { copy(isSubmitting = false, submitError = result.error, isUncertain = !definite) }
                }
            }
        }
    }

    fun onRoleChipClicked() {
        if (!current().isSubmitting) session.clearRole()
    }

    private fun load() {
        if (!current().canEdit || loadJob?.isActive == true || current().isSubmitting) return
        update { copy(isLoading = true, loadError = null) }
        loadJob =
            viewModelScope.launch {
                // Forced, so the band of every musician is the backend's current one and not an old cached copy.
                when (val result = repository.getPerformers(forceRefresh = true)) {
                    is DataResult.Failure -> {
                        update { copy(isLoading = false, loadError = result.error) }
                    }

                    is DataResult.Success -> {
                        filterJob?.cancel()
                        val state = current()
                        val (musicians, memberIds) =
                            withContext(defaultDispatcher) {
                                val musicians = result.value.filter { it.type == PerformerType.MUSICIAN }
                                val fresh = musicians.filter { it.bandId == bandId }.mapTo(mutableSetOf()) { it.id }
                                musicians to (initialMemberIds + fresh)
                            }
                        val selectedId =
                            state.selectedId?.takeIf { id -> id !in memberIds && musicians.any { it.id == id } }
                        savedState[SELECTED_ID] = selectedId
                        val query = state.query
                        val rows =
                            withContext(defaultDispatcher) {
                                buildBandMemberOptions(musicians, memberIds, bandId, query, selectedId)
                            }
                        // Rows and list arrive in the same state, so "no results" never flashes while loading.
                        update {
                            copy(
                                musicians = musicians,
                                memberIds = memberIds,
                                selectedId = selectedId,
                                visibleRows = rows,
                                isLoading = false,
                                loadError = null,
                            )
                        }
                        if (current().query != query) refreshVisibleRows()
                    }
                }
            }
    }

    /** Rebuilds the rows for the current search and selection; only the latest call publishes its result. */
    private fun refreshVisibleRows() {
        filterJob?.cancel()
        val state = current()
        if (state.isLoading || state.musicians.isEmpty()) return
        filterJob =
            viewModelScope.launch {
                val rows =
                    withContext(defaultDispatcher) {
                        buildBandMemberOptions(state.musicians, state.memberIds, bandId, state.query, state.selectedId)
                    }
                // withContext checks cancellation on return: a result of an older search is never published.
                update { copy(visibleRows = rows) }
            }
    }

    private fun current(): BandMemberPickerUiState = checkNotNull(_uiState.value)

    private fun update(change: BandMemberPickerUiState.() -> BandMemberPickerUiState) {
        _uiState.value = current().change()
    }

    private fun emit(event: BandMemberPickerEvent) {
        _events.value = Event(event)
    }

    companion object {
        private const val QUERY = "bandMemberQuery"
        private const val SELECTED_ID = "bandMemberSelectedId"
        private const val WRITE_PENDING = "bandMemberWritePending"
        private val DEFINITE_FAILURES = setOf(400, 404)

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VinilosApplication
                    BandMemberPickerViewModel(
                        createSavedStateHandle(),
                        app.container.performerRepository,
                        app.container.sessionRepository,
                    )
                }
            }
    }
}
