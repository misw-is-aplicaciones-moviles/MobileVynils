package co.edu.uniandes.vinilos.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.repositories.CollectorRepository
import co.edu.uniandes.vinilos.repositories.SessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Collector detail. One load joins the collector and their albums. A role change does not reload, and a finished load
 * is not repeated just because the view is recreated.
 */
class CollectorDetailViewModel(
    private val collectorId: Int,
    private val repository: CollectorRepository,
    private val session: SessionRepository,
) : ViewModel() {
    private val validIdentity = collectorId > 0
    private val _uiState =
        MediatorLiveData(
            CollectorDetailUiState(
                role = session.role.value,
                isLoading = validIdentity,
                error = if (validIdentity) null else DataError.Serialization,
                hasValidIdentity = validIdentity,
            ),
        ).apply { addSource(session.role) { role -> update { copy(role = role) } } }
    val uiState: LiveData<CollectorDetailUiState> = _uiState
    private val _events = MutableLiveData<Event<CollectorDetailEvent>>()
    val events: LiveData<Event<CollectorDetailEvent>> = _events
    private var loadJob: Job? = null
    private var navigationPending = false

    init {
        load(forceRefresh = false)
    }

    fun retry() = load(forceRefresh = true)

    fun onAlbumClicked(albumId: Int) {
        if (navigationPending || current().detail?.albums?.none { it.albumId == albumId } != false) return
        navigationPending = true
        _events.value = Event(CollectorDetailEvent.OpenAlbum(albumId))
    }

    fun onAddAlbumClicked() {
        val state = current()
        if (navigationPending || state.role != UserRole.COLLECTOR || state.detail == null) return
        navigationPending = true
        _events.value = Event(CollectorDetailEvent.OpenAlbumPicker(collectorId))
    }

    fun refreshAfterAlbumAdded() {
        load(forceRefresh = true)
    }

    fun onFavoriteClicked(performerId: Int) {
        if (navigationPending) return
        val favorite = current().detail?.favorites?.firstOrNull { it.id == performerId } ?: return
        val type = favorite.type ?: return
        navigationPending = true
        _events.value = Event(CollectorDetailEvent.OpenFavorite(performerId, type))
    }

    fun onNavigationReturned() {
        navigationPending = false
    }

    fun onRoleChipClicked() {
        session.clearRole()
        _events.value = Event(CollectorDetailEvent.OpenRoleSelector)
    }

    private fun load(forceRefresh: Boolean) {
        if (!validIdentity || loadJob?.isActive == true) return
        update { copy(detail = null, isLoading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                when (val result = repository.getCollector(collectorId, forceRefresh)) {
                    is DataResult.Success -> update { copy(detail = result.value, isLoading = false) }
                    is DataResult.Failure -> update { copy(detail = null, isLoading = false, error = result.error) }
                }
            }
    }

    private fun current() = checkNotNull(_uiState.value)

    private fun update(change: CollectorDetailUiState.() -> CollectorDetailUiState) {
        _uiState.value = current().change()
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    val app =
                        checkNotNull(application as? VinilosApplication) {
                            "CollectorDetailViewModel requires VinilosApplication"
                        }
                    val collectorId =
                        createSavedStateHandle().get<Int>(CollectorListViewModel.ARG_COLLECTOR_ID) ?: 0
                    CollectorDetailViewModel(
                        collectorId,
                        app.container.collectorRepository,
                        app.container.sessionRepository,
                    )
                }
            }
    }
}
