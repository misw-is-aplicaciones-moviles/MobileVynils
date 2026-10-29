package co.edu.uniandes.vinilos.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * Name and duration of a new track. Saving only reveals the field errors;
 * the album detail and the API arrive with the rest of HU08.
 */
class TrackCreateViewModel : ViewModel() {
    private val _uiState = MutableLiveData(TrackCreateUiState())
    val uiState: LiveData<TrackCreateUiState> = _uiState

    fun onNameChanged(name: String) {
        update { copy(name = name) }
    }

    fun onDigit(digit: Int) {
        if (digit !in DIGIT_MIN..DIGIT_MAX) return
        update { copy(durationValue = TrackDuration.append(durationValue, digit)) }
    }

    fun onDeleteDigit() {
        update { copy(durationValue = TrackDuration.deleteLast(durationValue)) }
    }

    fun onClearDuration() {
        update { copy(durationValue = TrackDuration.EMPTY_VALUE) }
    }

    fun onSave() {
        update { copy(submitAttempted = true) }
    }

    private fun update(change: TrackCreateUiState.() -> TrackCreateUiState) {
        _uiState.value = currentState().change()
    }

    private fun currentState(): TrackCreateUiState = checkNotNull(_uiState.value)

    private companion object {
        const val DIGIT_MIN = 0
        const val DIGIT_MAX = 9
    }
}
