package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.NewTrack

/** Pure Kotlin validation for HU08. */
internal object TrackCreateValidator {
    fun validate(
        name: String,
        durationValue: Int,
    ): TrackCreateValidation {
        val errors =
            TrackCreateErrors(
                name = if (name.isBlank()) TrackFieldError.REQUIRED else null,
                duration = if (TrackDuration.isValid(durationValue)) null else TrackFieldError.INVALID_DURATION,
            )
        if (errors.hasErrors) return TrackCreateValidation.Invalid(errors)
        return TrackCreateValidation.Valid(
            NewTrack(
                name = name.trim(),
                duration = TrackDuration.formatForRequest(durationValue),
            ),
        )
    }
}

internal sealed interface TrackCreateValidation {
    data class Valid(
        val draft: NewTrack,
    ) : TrackCreateValidation

    data class Invalid(
        val errors: TrackCreateErrors,
    ) : TrackCreateValidation
}
