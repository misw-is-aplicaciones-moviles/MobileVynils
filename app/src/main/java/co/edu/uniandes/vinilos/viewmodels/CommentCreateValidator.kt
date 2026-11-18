package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.NewComment

/** Pure Kotlin validation for HU09. The mockup offers one to five stars, so zero means "not chosen yet". */
internal object CommentCreateValidator {
    const val NO_RATING = 0
    const val MIN_RATING = 1
    const val MAX_RATING = 5

    fun validate(
        collectorId: Int?,
        rating: Int,
        description: String,
    ): CommentCreateValidation {
        val errors =
            CommentCreateErrors(
                collector = if (collectorId == null) CommentFieldError.REQUIRED else null,
                rating = if (rating in MIN_RATING..MAX_RATING) null else CommentFieldError.REQUIRED,
                // The backend saves a text of only spaces, so the app treats it as empty.
                description = if (description.isBlank()) CommentFieldError.REQUIRED else null,
            )
        if (errors.hasErrors || collectorId == null) return CommentCreateValidation.Invalid(errors)
        return CommentCreateValidation.Valid(
            NewComment(
                description = description.trim(),
                rating = rating,
                collectorId = collectorId,
            ),
        )
    }
}

internal sealed interface CommentCreateValidation {
    data class Valid(
        val draft: NewComment,
    ) : CommentCreateValidation

    data class Invalid(
        val errors: CommentCreateErrors,
    ) : CommentCreateValidation
}
