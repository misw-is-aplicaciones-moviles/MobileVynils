package co.edu.uniandes.vinilos.ui.albums

import co.edu.uniandes.vinilos.viewmodels.CommentCreateUiState

/**
 * Which parts of the "Comentar álbum" sheet must be drawn again when the state goes from [previous] to
 * [current] (HU09). Typing changes only the text, so it redraws none of these parts. A null [previous]
 * means a new view, which draws everything.
 */
internal data class CommentSheetRedraw(
    val ratingLabel: Boolean,
    val stars: Boolean,
    val errors: Boolean,
    val submitError: Boolean,
    val submitting: Boolean,
) {
    companion object {
        fun between(
            previous: CommentCreateUiState?,
            current: CommentCreateUiState,
        ): CommentSheetRedraw {
            if (previous == null) return EVERYTHING
            val ratingChanged = previous.rating != current.rating
            val submittingChanged = previous.isSubmitting != current.isSubmitting
            return CommentSheetRedraw(
                ratingLabel = ratingChanged,
                stars = ratingChanged || submittingChanged,
                errors = previous.errors != current.errors,
                submitError = previous.submitError != current.submitError,
                submitting = submittingChanged,
            )
        }

        private val EVERYTHING = CommentSheetRedraw(true, true, true, true, true)
    }
}
