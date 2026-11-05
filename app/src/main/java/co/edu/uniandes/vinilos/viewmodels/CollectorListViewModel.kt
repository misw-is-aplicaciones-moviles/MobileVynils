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
import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.repositories.CollectorRepository
import co.edu.uniandes.vinilos.repositories.SessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Collector screen. Shows what Room already has, revalidates once against the backend, and keeps the list on
 * screen when a refresh fails. The rows are computed in [defaultDispatcher], so the view only draws them.
 */
class CollectorListViewModel(
    private val collectorRepository: CollectorRepository,
    private val sessionRepository: SessionRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState =
        MediatorLiveData(CollectorListUiState()).apply {
            addSource(sessionRepository.role) { role -> update { copy(role = role) } }
        }
    val uiState: LiveData<CollectorListUiState> = _uiState

    private val _events = MutableLiveData<Event<CollectorListEvent>>()
    val events: LiveData<Event<CollectorListEvent>> = _events

    init {
        viewModelScope.launch { loadInitial() }
    }

    /** "Reintentar" and pull to refresh. Ignored while another load is running. */
    fun refresh() {
        val state = currentState()
        if (state.isLoading || state.isRefreshing) return
        val hasList = state.collectors.isNotEmpty()
        update { copy(isLoading = !hasList, isRefreshing = hasList, error = null) }
        viewModelScope.launch { publish(collectorRepository.getCollectors(forceRefresh = true)) }
    }

    fun onCollectorClicked(collectorId: Int) {
        _events.value = Event(CollectorListEvent.OpenDetail(collectorId))
    }

    fun onRoleChipClicked() {
        sessionRepository.clearRole()
        _events.value = Event(CollectorListEvent.OpenRoleSelector)
    }

    private suspend fun loadInitial() {
        if (!collectorRepository.hasCachedCollectors()) {
            publish(collectorRepository.getCollectors(forceRefresh = false))
            return
        }
        val cached = collectorRepository.getCollectors(forceRefresh = false)
        publish(cached)
        if (cached is DataResult.Success) {
            update { copy(isRefreshing = true) }
            publish(collectorRepository.getCollectors(forceRefresh = true))
        }
    }

    private suspend fun publish(result: DataResult<List<Collector>, DataError>) {
        when (result) {
            is DataResult.Success -> {
                val items = withContext(defaultDispatcher) { result.value.mapIndexed(::toItem) }
                update {
                    copy(
                        collectors = items,
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
        val hasList = currentState().collectors.isNotEmpty()
        update { copy(isLoading = false, isRefreshing = false, error = if (hasList) null else error) }
        if (hasList) {
            _events.value = Event(CollectorListEvent.ShowRefreshError(error))
        }
    }

    private fun toItem(
        index: Int,
        collector: Collector,
    ) = CollectorItem(
        id = collector.id,
        position = String.format(Locale.ROOT, POSITION_FORMAT, index + 1),
        initials = collectorInitials(collector.name),
        name = collector.name,
        email = collector.email,
        summary =
            if (collector.albumCount == 0) {
                CollectorSummaryLine.NoAlbums
            } else {
                CollectorSummaryLine.Counts(collector.albumCount, collector.favoriteCount)
            },
    )

    private fun currentState() = checkNotNull(_uiState.value)

    private fun update(change: CollectorListUiState.() -> CollectorListUiState) {
        _uiState.value = currentState().change()
    }

    companion object {
        /** Navigation argument of `collectorDetailFragment` (docs/10, sección 3). */
        const val ARG_COLLECTOR_ID = "collectorId"

        private const val POSITION_FORMAT = "%02d"

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    val app =
                        checkNotNull(application as? VinilosApplication) {
                            "CollectorListViewModel requires VinilosApplication"
                        }
                    CollectorListViewModel(app.container.collectorRepository, app.container.sessionRepository)
                }
            }
    }
}
