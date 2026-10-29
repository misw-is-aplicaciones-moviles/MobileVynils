package co.edu.uniandes.vinilos.viewmodels

/**
 * What the new-track sheet draws. Errors stay hidden until the collector taps "Guardar canción".
 */
data class TrackCreateUiState(
    val name: String = "",
    val durationValue: Int = TrackDuration.EMPTY_VALUE,
    val submitAttempted: Boolean = false,
) {
    val durationText: String get() = TrackDuration.format(durationValue)
    val hasDuration: Boolean get() = durationValue != TrackDuration.EMPTY_VALUE
    val isNameMissing: Boolean get() = submitAttempted && name.isBlank()
    val isDurationInvalid: Boolean get() = submitAttempted && !TrackDuration.isValid(durationValue)
}
