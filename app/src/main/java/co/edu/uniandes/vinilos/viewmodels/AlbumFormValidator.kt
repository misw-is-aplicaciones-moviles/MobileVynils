package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.NewAlbum

/** Rules of the new album form (docs/10): every field is required. Pure Kotlin, no Android. */
object AlbumFormValidator {
    fun validate(form: AlbumForm): AlbumFormResult {
        val genre = form.genre?.takeUnless { it == Genre.UNKNOWN }
        val releaseDate = form.releaseDate
        val recordLabel = form.recordLabel
        val errors =
            AlbumFormErrors(
                name = requiredText(form.name),
                coverUrl = requiredText(form.coverUrl),
                releaseDate = if (releaseDate == null) FieldError.REQUIRED else null,
                description = requiredText(form.description),
                genre = if (genre == null) FieldError.MISSING_GENRE else null,
                recordLabel = if (recordLabel == null) FieldError.MISSING_RECORD_LABEL else null,
            )
        if (errors.hasErrors) return AlbumFormResult.Invalid(errors)
        // Without errors these three are set: each null above produced an error.
        return AlbumFormResult.Valid(
            NewAlbum(
                name = form.name.trim(),
                coverUrl = form.coverUrl.trim(),
                releaseDate = checkNotNull(releaseDate),
                description = form.description.trim(),
                genre = checkNotNull(genre),
                recordLabel = checkNotNull(recordLabel),
            ),
        )
    }

    private fun requiredText(value: String): FieldError? = if (value.isBlank()) FieldError.REQUIRED else null
}

/** Why a field is invalid. The screen turns each one into a string resource. */
enum class FieldError {
    REQUIRED,
    MISSING_GENRE,
    MISSING_RECORD_LABEL,
}

/** One error, or null, per field of [AlbumForm]. */
data class AlbumFormErrors(
    val name: FieldError? = null,
    val coverUrl: FieldError? = null,
    val releaseDate: FieldError? = null,
    val description: FieldError? = null,
    val genre: FieldError? = null,
    val recordLabel: FieldError? = null,
) {
    val hasErrors: Boolean
        get() = listOf(name, coverUrl, releaseDate, description, genre, recordLabel).any { it != null }
}

/** Either the album ready to send or the errors to show. */
sealed interface AlbumFormResult {
    data class Valid(
        val album: NewAlbum,
    ) : AlbumFormResult

    data class Invalid(
        val errors: AlbumFormErrors,
    ) : AlbumFormResult
}
