package co.edu.uniandes.vinilos.viewmodels

import androidx.lifecycle.LiveData
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
import kotlinx.coroutines.launch

/** Validates and adds one song to the album whose id is supplied in the sheet arguments. */
class TrackCreateViewModel(
    private val albumId: Int,
    private val albumRepository: AlbumRepository,
) : ViewModel() {
    private val _uiState = MutableLiveData(TrackCreateUiState())
    val uiState: LiveData<TrackCreateUiState> = _uiState

    private val _events = MutableLiveData<Event<TrackCreateEvent>>()
    val events: LiveData<Event<TrackCreateEvent>> = _events

    init {
        require(albumId > 0) { "albumId must be positive" }
    }

    fun onNameChanged(name: String) {
        val state = currentState()
        if (state.isSubmitting || state.name == name) return
        update {
            copy(
                name = name,
                errors =
                    errors.copy(
                        name = if (validationAttempted && name.isBlank()) TrackFieldError.REQUIRED else null,
                    ),
                submitError = null,
            )
        }
    }

    fun onDigit(digit: Int) {
        if (digit !in DIGIT_MIN..DIGIT_MAX || currentState().isSubmitting) return
        update {
            val nextDuration = TrackDuration.append(durationValue, digit)
            copy(
                durationValue = nextDuration,
                errors = errors.copy(duration = nextDuration.validationError(validationAttempted)),
                submitError = null,
            )
        }
    }

    fun onDeleteDigit() {
        if (currentState().isSubmitting) return
        update {
            val nextDuration = TrackDuration.deleteLast(durationValue)
            copy(
                durationValue = nextDuration,
                errors = errors.copy(duration = nextDuration.validationError(validationAttempted)),
                submitError = null,
            )
        }
    }

    fun onClearDuration() {
        if (currentState().isSubmitting) return
        update {
            val nextDuration = TrackDuration.EMPTY_VALUE
            copy(
                durationValue = nextDuration,
                errors = errors.copy(duration = nextDuration.validationError(validationAttempted)),
                submitError = null,
            )
        }
    }

    fun onSave() {
        val state = currentState()
        if (state.isSubmitting) return
        when (val validation = TrackCreateValidator.validate(state.name, state.durationValue)) {
            is TrackCreateValidation.Invalid -> {
                update { copy(errors = validation.errors, validationAttempted = true, submitError = null) }
            }

            is TrackCreateValidation.Valid -> {
                update {
                    copy(
                        errors = TrackCreateErrors(),
                        validationAttempted = true,
                        isSubmitting = true,
                        submitError = null,
                    )
                }
                viewModelScope.launch {
                    when (val result = albumRepository.addTrack(albumId, validation.draft)) {
                        is DataResult.Success -> {
                            update { copy(isSubmitting = false) }
                            _events.value = Event(TrackCreateEvent.Created(result.value))
                        }

                        is DataResult.Failure -> {
                            update { copy(isSubmitting = false, submitError = result.error) }
                        }
                    }
                }
            }
        }
    }

    private fun update(change: TrackCreateUiState.() -> TrackCreateUiState) {
        _uiState.value = currentState().change()
    }

    private fun currentState(): TrackCreateUiState = checkNotNull(_uiState.value)

    private fun Int.validationError(validationAttempted: Boolean): TrackFieldError? =
        if (validationAttempted && !TrackDuration.isValid(this)) TrackFieldError.INVALID_DURATION else null

    companion object {
        const val ARG_ALBUM_ID = "albumId"

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VinilosApplication
                    val albumId = checkNotNull(createSavedStateHandle().get<Int>(ARG_ALBUM_ID))
                    TrackCreateViewModel(albumId, app.container.albumRepository)
                }
            }

        private const val DIGIT_MIN = 0
        private const val DIGIT_MAX = 9
    }
}
