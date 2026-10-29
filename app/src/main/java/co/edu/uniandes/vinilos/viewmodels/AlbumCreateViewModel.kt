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
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.RecordLabel
import co.edu.uniandes.vinilos.repositories.AlbumRepository
import co.edu.uniandes.vinilos.repositories.SessionRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * New album form. Keeps what the collector typed across rotations, validates on "Crear álbum"
 * and sends the album once. Each change clears the error of its own field.
 */
class AlbumCreateViewModel(
    private val albumRepository: AlbumRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val _uiState =
        MediatorLiveData(AlbumCreateUiState()).apply {
            addSource(sessionRepository.role) { role -> update { copy(role = role) } }
        }
    val uiState: LiveData<AlbumCreateUiState> = _uiState

    private val _events = MutableLiveData<Event<AlbumCreateEvent>>()
    val events: LiveData<Event<AlbumCreateEvent>> = _events

    fun onNameChanged(name: String) {
        if (currentState().form.name == name) return
        update { copy(form = form.copy(name = name), errors = errors.copy(name = null)) }
    }

    fun onCoverChanged(coverUrl: String) {
        if (currentState().form.coverUrl == coverUrl) return
        update { copy(form = form.copy(coverUrl = coverUrl), errors = errors.copy(coverUrl = null)) }
    }

    fun onReleaseDateSelected(releaseDate: LocalDate) {
        update { copy(form = form.copy(releaseDate = releaseDate), errors = errors.copy(releaseDate = null)) }
    }

    fun onDescriptionChanged(description: String) {
        if (currentState().form.description == description) return
        update { copy(form = form.copy(description = description), errors = errors.copy(description = null)) }
    }

    fun onGenreSelected(genre: Genre?) {
        if (currentState().form.genre == genre) return
        update { copy(form = form.copy(genre = genre), errors = errors.copy(genre = null)) }
    }

    fun onRecordLabelSelected(recordLabel: RecordLabel?) {
        if (currentState().form.recordLabel == recordLabel) return
        update { copy(form = form.copy(recordLabel = recordLabel), errors = errors.copy(recordLabel = null)) }
    }

    /** "Crear álbum". Ignored while a previous submit is still running, so a double tap creates one album. */
    fun onSubmit() {
        val state = currentState()
        if (state.isSubmitting) return
        when (val result = AlbumFormValidator.validate(state.form)) {
            is AlbumFormResult.Invalid -> {
                update { copy(errors = result.errors) }
            }

            is AlbumFormResult.Valid -> {
                update { copy(errors = AlbumFormErrors(), isSubmitting = true) }
                viewModelScope.launch {
                    when (val created = albumRepository.createAlbum(result.album)) {
                        // isSubmitting stays true: the screen closes and must not accept another tap meanwhile.
                        is DataResult.Success -> {
                            _events.value = Event(AlbumCreateEvent.Created)
                        }

                        is DataResult.Failure -> {
                            update { copy(isSubmitting = false) }
                            _events.value = Event(AlbumCreateEvent.Failed(created.error))
                        }
                    }
                }
            }
        }
    }

    fun onCancel() {
        _events.value = Event(AlbumCreateEvent.Cancelled)
    }

    fun onRoleChipClicked() {
        sessionRepository.clearRole()
        _events.value = Event(AlbumCreateEvent.OpenRoleSelector)
    }

    private fun currentState() = checkNotNull(_uiState.value)

    private fun update(change: AlbumCreateUiState.() -> AlbumCreateUiState) {
        _uiState.value = currentState().change()
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    val app =
                        checkNotNull(application as? VinilosApplication) {
                            "AlbumCreateViewModel requires VinilosApplication"
                        }
                    AlbumCreateViewModel(app.container.albumRepository, app.container.sessionRepository)
                }
            }
    }
}
