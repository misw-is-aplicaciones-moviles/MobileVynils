package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.NewComment
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("MagicNumber") // Collector ids and ratings of the form.
class CommentCreateValidatorTest {
    private val required = CommentFieldError.REQUIRED

    @Test
    fun `valid form returns the draft with the text trimmed and inner spaces kept`() {
        val result = CommentCreateValidator.validate(101, 4, "  Una obra\n que no envejece.\t")

        assertEquals(
            CommentCreateValidation.Valid(NewComment("Una obra\n que no envejece.", 4, 101)),
            result,
        )
    }

    @Test
    fun `each missing field flags only its own error`() {
        assertEquals(
            CommentCreateValidation.Invalid(CommentCreateErrors(collector = required)),
            CommentCreateValidator.validate(null, 4, "Texto"),
        )
        assertEquals(
            CommentCreateValidation.Invalid(CommentCreateErrors(rating = required)),
            CommentCreateValidator.validate(101, CommentCreateValidator.NO_RATING, "Texto"),
        )
        assertEquals(
            CommentCreateValidation.Invalid(CommentCreateErrors(description = required)),
            CommentCreateValidator.validate(101, 4, ""),
        )
    }

    @Test
    fun `an empty form flags every field`() {
        assertEquals(
            CommentCreateValidation.Invalid(CommentCreateErrors(required, required, required)),
            CommentCreateValidator.validate(null, CommentCreateValidator.NO_RATING, ""),
        )
    }

    @Test
    fun `a text of only spaces or line breaks is required because the backend would save it`() {
        listOf(" ", "   ", "\n\t ").forEach { text ->
            assertEquals(
                text,
                CommentCreateValidation.Invalid(CommentCreateErrors(description = required)),
                CommentCreateValidator.validate(101, 4, text),
            )
        }
    }

    @Test
    fun `ratings one and five pass and zero and six are required`() {
        listOf(1, 5).forEach { rating ->
            assertEquals(
                "rating $rating",
                CommentCreateValidation.Valid(NewComment("Texto", rating, 101)),
                CommentCreateValidator.validate(101, rating, "Texto"),
            )
        }
        listOf(0, 6, -1).forEach { rating ->
            assertEquals(
                "rating $rating",
                CommentCreateValidation.Invalid(CommentCreateErrors(rating = required)),
                CommentCreateValidator.validate(101, rating, "Texto"),
            )
        }
    }
}
