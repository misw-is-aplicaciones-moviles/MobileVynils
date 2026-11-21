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
    fun a_new_view_draws_every_part() {
        val redraw = CommentSheetRedraw.between(null, filled)

        assertEquals(CommentSheetRedraw(true, true, true, true, true), redraw)
    }

    @Test
    fun typing_with_a_fixed_rating_redraws_none_of_the_parts() {
        var previous = filled
        "eno, lo recomiendo a todos".forEach { letter ->
            val current = previous.copy(description = previous.description + letter)

            assertEquals(nothing, CommentSheetRedraw.between(previous, current))
            previous = current
        }
    }

    @Test
    fun another_rating_redraws_the_label_and_the_stars_only() {
        val redraw = CommentSheetRedraw.between(filled, filled.copy(rating = 2))

        assertEquals(nothing.copy(ratingLabel = true, stars = true), redraw)
    }

    @Test
    fun starting_and_ending_a_submission_redraws_the_stars_and_the_buttons_with_the_same_rating() {
        val submitting = filled.copy(isSubmitting = true)

        assertEquals(nothing.copy(stars = true, submitting = true), CommentSheetRedraw.between(filled, submitting))
        assertEquals(nothing.copy(stars = true, submitting = true), CommentSheetRedraw.between(submitting, filled))
    }

    @Test
    fun a_validation_error_redraws_only_the_errors_and_clearing_it_does_too() {
        val required = CommentCreateErrors(description = CommentFieldError.REQUIRED)
        val missing = filled.copy(description = "", errors = required)

        assertEquals(nothing.copy(errors = true), CommentSheetRedraw.between(filled, missing))
        assertEquals(nothing.copy(errors = true), CommentSheetRedraw.between(missing, filled))
    }

    @Test
    fun a_failed_submission_redraws_the_error_message_and_the_buttons() {
        val failed = filled.copy(submitError = DataError.Network)

        val redraw = CommentSheetRedraw.between(filled.copy(isSubmitting = true), failed)

        assertEquals(nothing.copy(stars = true, submitError = true, submitting = true), redraw)
    }
}
