package co.edu.uniandes.vinilos.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.models.AlbumStatus
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.NewCollectorAlbum
import co.edu.uniandes.vinilos.repositories.CollectorRepository
import kotlinx.coroutines.launch

/** Price and status for adding an album to a collector's collection. */
class CollectorAlbumAddViewModel(
    private val savedState: SavedStateHandle,
    private val repository: CollectorRepository,
) : ViewModel() {
    val collectorId: Int = savedState[ARG_COLLECTOR_ID] ?: 0
    val albumId: Int = savedState[ARG_ALBUM_ID] ?: 0
    val albumName: String = savedState[ARG_ALBUM_NAME] ?: ""
    val artistName: String = savedState[ARG_ARTIST_NAME] ?: ""

    private val _uiState =
        MutableLiveData(
            CollectorAlbumAddUiState(
                albumName = albumName,
                artistName = artistName,
                priceDigits = savedState[PRICE_DIGITS] ?: 0,
                status = savedState.get<String>(STATUS_KEY)?.let { AlbumStatus.valueOf(it) },
            ),
        )
    val uiState: LiveData<CollectorAlbumAddUiState> = _uiState

    private val _events = MutableLiveData<Event<CollectorAlbumAddEvent>>()
    val events: LiveData<Event<CollectorAlbumAddEvent>> = _events

    fun onDigit(digit: Int) {
        if (digit !in 0..9 || current().isSubmitting) return
        update {
            val next = (priceDigits * 10 + digit).coerceAtMost(MAX_PRICE)
            copy(priceDigits = next, validationAttempted = false, submitError = null)
        }
    }

    fun onDeleteDigit() {
        if (current().isSubmitting) return
        update {
            copy(
                priceDigits = priceDigits / 10,
                validationAttempted = false,
                submitError = null,
            )
        }
    }

    fun onClearPrice() {
        if (current().isSubmitting) return
        update { copy(priceDigits = 0, validationAttempted = false, submitError = null) }
    }

    fun onStatusSelected(status: AlbumStatus) {
        if (current().isSubmitting) return
        savedState[STATUS_KEY] = status.name
        update { copy(status = status, validationAttempted = false, submitError = null) }
    }

    fun onSave() {
        val state = current()
        if (state.isSubmitting) return
        if (state.priceDigits <= 0 || state.status == null) {
            update { copy(validationAttempted = true) }
            return
        }
        val draft = NewCollectorAlbum(price = state.priceDigits, status = checkNotNull(state.status))
        update { copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            when (val result = repository.addAlbum(collectorId, albumId, draft)) {
                is DataResult.Success -> {
                    update { copy(isSubmitting = false) }
                    _events.value = Event(CollectorAlbumAddEvent.Added)
                }

                is DataResult.Failure -> {
                    update { copy(isSubmitting = false, submitError = result.error) }
                }
            }
        }
    }

    private fun current(): CollectorAlbumAddUiState = checkNotNull(_uiState.value)

    private fun update(change: CollectorAlbumAddUiState.() -> CollectorAlbumAddUiState) {
        _uiState.value = current().change()
    }

    companion object {
        const val ARG_COLLECTOR_ID = "collectorId"
        const val ARG_ALBUM_ID = "albumId"
        const val ARG_ALBUM_NAME = "albumName"
        const val ARG_ARTIST_NAME = "artistName"

        private const val PRICE_DIGITS = "collectorAlbumAddPrice"
        private const val STATUS_KEY = "collectorAlbumAddStatus"
        private const val MAX_PRICE = 999_999

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VinilosApplication
                    CollectorAlbumAddViewModel(
                        createSavedStateHandle(),
                        app.container.collectorRepository,
                    )
                }
            }
    }
}
