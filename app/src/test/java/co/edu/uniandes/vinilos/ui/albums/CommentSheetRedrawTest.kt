package co.edu.uniandes.vinilos.ui.albums

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.viewmodels.CommentCreateErrors
import co.edu.uniandes.vinilos.viewmodels.CommentCreateUiState
import co.edu.uniandes.vinilos.viewmodels.CommentFieldError
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentSheetRedrawTest {
    private val filled = CommentCreateUiState(selectedCollectorId = 1, rating = 4, description = "Muy bu")
    private val nothing = CommentSheetRedraw(false, false, false, false, false)

    @Test
    fun `a new view draws every part`() {
        val redraw = CommentSheetRedraw.between(null, filled)

        assertEquals(CommentSheetRedraw(true, true, true, true, true), redraw)
    }

    @Test
    fun `typing with a fixed rating redraws none of the parts`() {
        var previous = filled
        "eno, lo recomiendo a todos".forEach { letter ->
            val current = previous.copy(description = previous.description + letter)

            assertEquals(nothing, CommentSheetRedraw.between(previous, current))
            previous = current
        }
    }

    @Test
    fun `another rating redraws the label and the stars only`() {
        val redraw = CommentSheetRedraw.between(filled, filled.copy(rating = 2))

        assertEquals(nothing.copy(ratingLabel = true, stars = true), redraw)
    }

    @Test
    fun `starting and ending a submission redraws the stars and the buttons with the same rating`() {
        val submitting = filled.copy(isSubmitting = true)

        assertEquals(nothing.copy(stars = true, submitting = true), CommentSheetRedraw.between(filled, submitting))
        assertEquals(nothing.copy(stars = true, submitting = true), CommentSheetRedraw.between(submitting, filled))
    }

    @Test
    fun `a validation error redraws only the errors and clearing it does too`() {
        val required = CommentCreateErrors(description = CommentFieldError.REQUIRED)
        val missing = filled.copy(description = "", errors = required)

        assertEquals(nothing.copy(errors = true), CommentSheetRedraw.between(filled, missing))
        assertEquals(nothing.copy(errors = true), CommentSheetRedraw.between(missing, filled))
    }

    @Test
    fun `a failed submission redraws the error message and the buttons`() {
        val failed = filled.copy(submitError = DataError.Network)

        val redraw = CommentSheetRedraw.between(filled.copy(isSubmitting = true), failed)

        assertEquals(nothing.copy(stars = true, submitError = true, submitting = true), redraw)
    }
}
