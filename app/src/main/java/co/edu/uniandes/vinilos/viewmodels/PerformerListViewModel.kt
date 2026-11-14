package co.edu.uniandes.vinilos.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.repositories.PerformerRepository
import co.edu.uniandes.vinilos.repositories.SessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Artist screen. Shows what Room already has, revalidates once against the backend, and keeps the list on
 * screen when a refresh fails. The rows are built in [defaultDispatcher], so the main thread only draws them.
 * The Todos / Músicos / Bandas filter is applied here to the list already loaded: choosing a chip never calls
 * the repository.
 */
class PerformerListViewModel(
    private val performerRepository: PerformerRepository,
    private val sessionRepository: SessionRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState =
        MediatorLiveData(PerformerListUiState()).apply {
            addSource(sessionRepository.role) { role -> update { copy(role = role) } }
        }
    val uiState: LiveData<PerformerListUiState> = _uiState

    private val _events = MutableLiveData<Event<PerformerListEvent>>()
    val events: LiveData<Event<PerformerListEvent>> = _events

    // The complete list, before the filter. The state only exposes the part the chip lets through.
    private var loaded: List<PerformerItem> = emptyList()

    init {
        viewModelScope.launch { loadInitial() }
    }

    /** "Reintentar" and pull to refresh. Ignored while another load is running. */
    fun refresh() {
        val state = currentState()
        if (state.isLoading || state.isRefreshing) return
        val hasList = loaded.isNotEmpty()
        update { copy(isLoading = !hasList, isRefreshing = hasList, error = null) }
        viewModelScope.launch { publish(performerRepository.getPerformers(forceRefresh = true)) }
    }

    fun onFilterSelected(filter: PerformerFilter) {
        if (filter == currentState().filter) return
        update { copy(filter = filter, performers = visibleFor(filter)) }
    }

    fun onPerformerClicked(
        performerId: Int,
        type: PerformerType,
    ) {
        _events.value = Event(PerformerListEvent.OpenDetail(performerId, type))
    }

    fun onRoleChipClicked() {
        sessionRepository.clearRole()
        _events.value = Event(PerformerListEvent.OpenRoleSelector)
    }

    private suspend fun loadInitial() {
        if (!performerRepository.hasCachedPerformers()) {
            publish(performerRepository.getPerformers(forceRefresh = false))
            return
        }
        val cached = performerRepository.getPerformers(forceRefresh = false)
        publish(cached)
        if (cached is DataResult.Success) {
            update { copy(isRefreshing = true) }
            publish(performerRepository.getPerformers(forceRefresh = true))
        }
    }

    private suspend fun publish(result: DataResult<List<Performer>, DataError>) {
        when (result) {
            is DataResult.Success -> {
                loaded = withContext(defaultDispatcher) { result.value.map(::toItem) }
                // `filter` is read here, after the rows are built: a chip chosen meanwhile is the one applied.
                update {
                    copy(
                        performers = visibleFor(filter),
                        hasPerformers = loaded.isNotEmpty(),
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                    )
                }
            }

            is DataResult.Failure -> {
                publishFailure(result.error)
            }
        }
    }

    private fun publishFailure(error: DataError) {
        val hasList = loaded.isNotEmpty()
        update { copy(isLoading = false, isRefreshing = false, error = if (hasList) null else error) }
        if (hasList) {
            _events.value = Event(PerformerListEvent.ShowRefreshError(error))
        }
    }

    // Todos lets everything through, so it shares the loaded list instead of copying it. `loaded` is never mutated,
    // only replaced, so sharing it with the state is safe.
    private fun visibleFor(filter: PerformerFilter): List<PerformerItem> =
        if (filter == PerformerFilter.ALL) loaded else loaded.filter { filter.accepts(it.type) }

    private fun toItem(performer: Performer) =
        PerformerItem(
            id = performer.id,
            type = performer.type,
            name = performer.name,
            imageUrl = performer.imageUrl,
            year = performer.date.year,
            summary = summaryOf(performer),
        )

    private fun summaryOf(performer: Performer): PerformerSummaryLine =
        when {
            performer.type == PerformerType.BAND -> {
                PerformerSummaryLine.BandCounts(performer.albumCount, performer.memberCount)
            }

            performer.bandName != null -> {
                PerformerSummaryLine.MemberOf(performer.bandName)
            }

            else -> {
                PerformerSummaryLine.AlbumCount(performer.albumCount)
            }
        }

    private fun currentState() = checkNotNull(_uiState.value)

    private fun update(change: PerformerListUiState.() -> PerformerListUiState) {
        _uiState.value = currentState().change()
    }

    companion object {
        /** Navigation arguments of `performerDetailFragment` (docs/10, sección 3). HU04 reads them. */
        const val ARG_PERFORMER_ID = "performerId"
        const val ARG_PERFORMER_TYPE = "type"

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    val app =
                        checkNotNull(application as? VinilosApplication) {
                            "PerformerListViewModel requires VinilosApplication"
                        }
                    PerformerListViewModel(app.container.performerRepository, app.container.sessionRepository)
                }
            }
    }
}
