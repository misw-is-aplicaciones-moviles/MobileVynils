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
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.repositories.AlbumRepository
import co.edu.uniandes.vinilos.repositories.SessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Loads a single detail per ViewModel instance and retries only when no request is active. */
class AlbumDetailViewModel(
    private val albumId: Int,
    private val albumRepository: AlbumRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val _uiState =
        MediatorLiveData(AlbumDetailUiState(role = sessionRepository.role.value)).apply {
            addSource(sessionRepository.role) { role -> update { copy(role = role) } }
        }
    val uiState: LiveData<AlbumDetailUiState> = _uiState
    private val _events = MutableLiveData<Event<AlbumDetailEvent>>()
    val events: LiveData<Event<AlbumDetailEvent>> = _events
    private var loadJob: Job? = null

    init {
        require(albumId > 0) { "albumId must be positive" }
        load(forceRefresh = false)
    }

    fun retry() {
        load(forceRefresh = true)
    }

    fun onRoleChipClicked() {
        sessionRepository.clearRole()
        _events.value = Event(AlbumDetailEvent.OpenRoleSelector)
    }

    private fun load(forceRefresh: Boolean) {
        if (loadJob?.isActive == true) return
        update { copy(album = null, isLoading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                when (val result = albumRepository.getAlbum(albumId, forceRefresh)) {
                    is DataResult.Success -> update { copy(album = result.value, isLoading = false) }
                    is DataResult.Failure -> update { copy(isLoading = false, error = result.error) }
                }
            }
    }

    private fun update(change: AlbumDetailUiState.() -> AlbumDetailUiState) {
        _uiState.value = checkNotNull(_uiState.value).change()
    }

    companion object {
        const val ARG_ALBUM_ID = "albumId"

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VinilosApplication
                    val id = checkNotNull(createSavedStateHandle().get<Int>(ARG_ALBUM_ID))
                    AlbumDetailViewModel(id, app.container.albumRepository, app.container.sessionRepository)
                }
            }
    }
}
