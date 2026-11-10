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
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.repositories.AlbumRepository
import co.edu.uniandes.vinilos.repositories.SessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Catalog screen. Shows what Room already has, revalidates once against the backend,
 * and keeps the list on screen when a refresh fails.
 */
class AlbumListViewModel(
    private val albumRepository: AlbumRepository,
    private val sessionRepository: SessionRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState =
        MediatorLiveData(AlbumListUiState()).apply {
            addSource(sessionRepository.role) { role -> update { copy(role = role) } }
        }
    val uiState: LiveData<AlbumListUiState> = _uiState

    private val _events = MutableLiveData<Event<AlbumListEvent>>()
    val events: LiveData<Event<AlbumListEvent>> = _events

    init {
        viewModelScope.launch { loadInitial() }
    }

    /** "Reintentar" and pull to refresh. Ignored while another load is running. */
    fun refresh() {
        val state = currentState()
        if (state.isLoading || state.isRefreshing) return
        val hasList = state.albums.isNotEmpty()
        update { copy(isLoading = !hasList, isRefreshing = hasList, error = null) }
        viewModelScope.launch { publish(albumRepository.getAlbums(forceRefresh = true)) }
    }

    fun onAlbumClicked(albumId: Int) {
        _events.value = Event(AlbumListEvent.OpenDetail(albumId))
    }

    fun onCreateAlbumClicked() {
        _events.value = Event(AlbumListEvent.OpenCreateAlbum)
    }

    /** The new album form just saved an album in Room: show the list with it and confirm. */
    fun onAlbumCreated() {
        viewModelScope.launch {
            publish(albumRepository.getAlbums(forceRefresh = false))
            _events.value = Event(AlbumListEvent.ShowAlbumCreated)
        }
    }

    fun onRoleChipClicked() {
        sessionRepository.clearRole()
        _events.value = Event(AlbumListEvent.OpenRoleSelector)
    }

    private suspend fun loadInitial() {
        if (!albumRepository.hasCachedAlbums()) {
            publish(albumRepository.getAlbums(forceRefresh = false))
            return
        }
        val cached = albumRepository.getAlbums(forceRefresh = false)
        publish(cached)
        if (cached is DataResult.Success) {
            update { copy(isRefreshing = true) }
            publish(albumRepository.getAlbums(forceRefresh = true))
        }
    }

    private suspend fun publish(result: DataResult<List<Album>, DataError>) {
        when (result) {
            is DataResult.Success -> {
                val items = withContext(defaultDispatcher) { result.value.mapIndexed(::toItem) }
                update {
                    copy(
                        albums = items,
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
        val hasList = currentState().albums.isNotEmpty()
        update { copy(isLoading = false, isRefreshing = false, error = if (hasList) null else error) }
        if (hasList) {
            _events.value = Event(AlbumListEvent.ShowRefreshError(error))
        }
    }

    private fun toItem(
        index: Int,
        album: Album,
    ) = AlbumItem(
        id = album.id,
        position = (index + 1).toString().padStart(2, '0'),
        name = album.name,
        artistName = album.performerName,
        genreName = album.genre.apiName,
        year = album.releaseDate.year,
        coverUrl = album.coverUrl,
    )

    private fun currentState() = checkNotNull(_uiState.value)

    private fun update(change: AlbumListUiState.() -> AlbumListUiState) {
        _uiState.value = currentState().change()
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    val app =
                        checkNotNull(application as? VinilosApplication) {
                            "AlbumListViewModel requires VinilosApplication"
                        }
                    AlbumListViewModel(app.container.albumRepository, app.container.sessionRepository)
                }
            }
    }
}
