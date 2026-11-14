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
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.repositories.AlbumRepository
import co.edu.uniandes.vinilos.repositories.CollectorRepository
import co.edu.uniandes.vinilos.repositories.SessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads the catalog and current collection, then keeps the selection local until the next HU11 step.
 * The visible rows are built in [defaultDispatcher], once per search or selection, so typing never filters the
 * catalog on the main thread; a newer search cancels the previous one so only the latest result is published.
 */
class CollectorAlbumPickerViewModel(
    private val savedState: SavedStateHandle,
    private val albumRepository: AlbumRepository,
    private val collectorRepository: CollectorRepository,
    private val sessionRepository: SessionRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val collectorId: Int = savedState[ARG_COLLECTOR_ID] ?: 0
    private val validIdentity = collectorId > 0
    private val _uiState =
        MediatorLiveData(
            CollectorAlbumPickerUiState(
                collectorId = collectorId,
                query = savedState[QUERY] ?: "",
                selectedAlbumId = savedState[SELECTED_ALBUM_ID],
                isLoading = validIdentity && sessionRepository.role.value == UserRole.COLLECTOR,
                error = if (validIdentity) null else DataError.Serialization,
                role = sessionRepository.role.value,
                hasValidIdentity = validIdentity,
            ),
        )
    val uiState: LiveData<CollectorAlbumPickerUiState> = _uiState

    private val _events = MutableLiveData<Event<CollectorAlbumPickerEvent>>()
    val events: LiveData<Event<CollectorAlbumPickerEvent>> = _events
    private var loadJob: Job? = null
    private var filterJob: Job? = null

    init {
        _uiState.addSource(sessionRepository.role) { role ->
            val previousRole = current().role
            update { copy(role = role) }
            if (previousRole == UserRole.COLLECTOR && role != UserRole.COLLECTOR) {
                loadJob?.cancel()
                emit(CollectorAlbumPickerEvent.OpenRoleSelector)
            }
        }
        when {
            !validIdentity -> Unit
            sessionRepository.role.value == UserRole.COLLECTOR -> load(forceRefresh = false)
            else -> emit(CollectorAlbumPickerEvent.OpenRoleSelector)
        }
    }

    fun retry() = load(forceRefresh = true)

    fun onQueryChanged(query: String) {
        savedState[QUERY] = query
        update { copy(query = query) }
        refreshVisibleAlbums()
    }

    fun onAlbumSelected(albumId: Int) {
        val state = current()
        if (state.role != UserRole.COLLECTOR || albumId in state.addedAlbumIds) return
        if (state.albums.none { it.id == albumId }) return
        savedState[SELECTED_ALBUM_ID] = albumId
        update { copy(selectedAlbumId = albumId) }
        refreshVisibleAlbums()
    }

    fun onContinue() {
        val state = current()
        val album = state.selectedAlbum ?: return
        if (!state.canContinue) return
        emit(CollectorAlbumPickerEvent.ContinueWithAlbum(collectorId, album.id, album.name))
    }

    fun onRoleChipClicked() {
        sessionRepository.clearRole()
        emit(CollectorAlbumPickerEvent.OpenRoleSelector)
    }

    private fun load(forceRefresh: Boolean) {
        if (!validIdentity || current().role != UserRole.COLLECTOR || loadJob?.isActive == true) return
        update { copy(isLoading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                val (catalog, detail) =
                    coroutineScope {
                        val catalog = async { albumRepository.getAlbums(forceRefresh) }
                        val detail = async { collectorRepository.getCollector(collectorId, forceRefresh) }
                        catalog.await() to detail.await()
                    }
                val error =
                    when {
                        catalog is DataResult.Failure -> catalog.error
                        detail is DataResult.Failure -> detail.error
                        else -> null
                    }
                if (error != null) {
                    update { copy(isLoading = false, error = error) }
                    return@launch
                }

                val albums = (catalog as DataResult.Success).value
                val collectorDetail = (detail as DataResult.Success).value
                val addedIds = collectorDetail.albums.mapTo(mutableSetOf()) { it.albumId }
                val selectedAlbumId =
                    current().selectedAlbumId?.takeIf { selectedId ->
                        albums.any { it.id == selectedId } && selectedId !in addedIds
                    }
                savedState[SELECTED_ALBUM_ID] = selectedAlbumId
                // A search typed while loading is cancelled here and recomputed below with the loaded catalog.
                filterJob?.cancel()
                val query = current().query
                val rows =
                    withContext(defaultDispatcher) { buildVisibleAlbums(albums, addedIds, query, selectedAlbumId) }
                // Rows and catalog arrive in the same state, so "no results" never flashes while loading.
                update {
                    copy(
                        collectorName = collectorDetail.collector.name,
                        albums = albums,
                        addedAlbumIds = addedIds,
                        selectedAlbumId = selectedAlbumId,
                        visibleAlbums = rows,
                        isLoading = false,
                        error = null,
                    )
                }
                if (current().query != query) refreshVisibleAlbums()
            }
    }

    /** Rebuilds the rows for the current search and selection; only the latest call publishes its result. */
    private fun refreshVisibleAlbums() {
        filterJob?.cancel()
        val state = current()
        if (state.isLoading || state.albums.isEmpty()) return
        filterJob =
            viewModelScope.launch {
                val rows =
                    withContext(defaultDispatcher) {
                        buildVisibleAlbums(state.albums, state.addedAlbumIds, state.query, state.selectedAlbumId)
                    }
                // withContext checks cancellation on return: a result of an older search is never published.
                update { copy(visibleAlbums = rows) }
            }
    }

    private fun current(): CollectorAlbumPickerUiState = checkNotNull(_uiState.value)

    private fun update(change: CollectorAlbumPickerUiState.() -> CollectorAlbumPickerUiState) {
        _uiState.value = current().change()
    }

    private fun emit(event: CollectorAlbumPickerEvent) {
        _events.value = Event(event)
    }

    companion object {
        const val ARG_COLLECTOR_ID = "collectorId"
        const val RESULT_ALBUM_SELECTED = "collector_album_selected"
        const val RESULT_COLLECTOR_ID = "collector_album_selected_collector_id"
        const val RESULT_ALBUM_ID = "collector_album_selected_album_id"
        const val RESULT_ALBUM_NAME = "collector_album_selected_album_name"

        private const val QUERY = "collectorAlbumPickerQuery"
        private const val SELECTED_ALBUM_ID = "collectorAlbumPickerSelectedAlbumId"

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VinilosApplication
                    CollectorAlbumPickerViewModel(
                        createSavedStateHandle(),
                        app.container.albumRepository,
                        app.container.collectorRepository,
                        app.container.sessionRepository,
                    )
                }
            }
    }
}
