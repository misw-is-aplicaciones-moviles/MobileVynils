package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.DataError

/** Everything the new-track sheet draws. Errors are typed until the Fragment maps them to strings. */
data class TrackCreateUiState(
    val name: String = "",
    val durationValue: Int = TrackDuration.EMPTY_VALUE,
    val errors: TrackCreateErrors = TrackCreateErrors(),
    val validationAttempted: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: DataError? = null,
) {
    val durationText: String get() = TrackDuration.format(durationValue)
    val hasDuration: Boolean get() = durationValue != TrackDuration.EMPTY_VALUE
    val isNameMissing: Boolean get() = errors.name == TrackFieldError.REQUIRED
    val isDurationInvalid: Boolean get() = errors.duration == TrackFieldError.INVALID_DURATION
}

/** One validation error per field. */
data class TrackCreateErrors(
    val name: TrackFieldError? = null,
    val duration: TrackFieldError? = null,
) {
    val hasErrors: Boolean get() = name != null || duration != null
}

enum class TrackFieldError {
    REQUIRED,
    INVALID_DURATION,
}
