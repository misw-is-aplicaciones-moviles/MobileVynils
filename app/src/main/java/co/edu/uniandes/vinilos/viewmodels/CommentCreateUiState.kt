package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.DataError

/** Everything the "Comentar álbum" sheet draws. Errors are typed until the Fragment maps them to strings. */
data class CommentCreateUiState(
    val collectors: List<CommentCollectorOption> = emptyList(),
    val isLoadingCollectors: Boolean = true,
    val collectorsError: DataError? = null,
    val selectedCollectorId: Int? = null,
    val rating: Int = CommentCreateValidator.NO_RATING,
    val description: String = "",
    val errors: CommentCreateErrors = CommentCreateErrors(),
    val isSubmitting: Boolean = false,
    val submitError: DataError? = null,
) {
    val hasRating: Boolean get() = rating != CommentCreateValidator.NO_RATING
    val isCollectorMissing: Boolean get() = errors.collector == CommentFieldError.REQUIRED
    val isRatingMissing: Boolean get() = errors.rating == CommentFieldError.REQUIRED
    val isDescriptionMissing: Boolean get() = errors.description == CommentFieldError.REQUIRED
}

/** One chip of "¿Quién comenta?": the initials are computed once, off the main thread, when the list arrives. */
data class CommentCollectorOption(
    val id: Int,
    val name: String,
    val initials: String,
)

/** One validation error per field. */
data class CommentCreateErrors(
    val collector: CommentFieldError? = null,
    val rating: CommentFieldError? = null,
    val description: CommentFieldError? = null,
) {
    val hasErrors: Boolean get() = collector != null || rating != null || description != null
}

enum class CommentFieldError {
    REQUIRED,
}
