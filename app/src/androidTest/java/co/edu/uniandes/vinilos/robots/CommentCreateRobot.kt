package co.edu.uniandes.vinilos.robots

import android.view.View
import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.FailureHandler
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.isNotSelected
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.bringIntoView
import co.edu.uniandes.vinilos.testing.clickListener
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf

private const val STARS = 5
private const val POLL_MILLIS = 200L
private const val CHIP_TIMEOUT_MILLIS = 15_000L
private val rethrow = FailureHandler { error, _ -> throw error }

/** Page Object de la hoja "Comentar álbum": quién comenta, la valoración con estrellas y el texto. */
class CommentCreateRobot {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    fun assertOpen(albumName: String): CommentCreateRobot {
        // La hoja puede estar desplazada por interacciones previas, así que se lleva el título a la vista.
        onView(withId(R.id.sheetTitle))
            .perform(bringIntoView())
            .check(matches(allOf(isDisplayed(), withText(R.string.comment_sheet_title))))
        onView(withId(R.id.sheetSubtitle)).check(matches(withText(albumName)))
        return this
    }

    /** Espera el chip de [name]: los coleccionistas llegan después de abrir la hoja. */
    fun chooseCollector(name: String): CommentCreateRobot {
        // En API 21 el toque sobre el centro visible del chip no lo marca.
        awaitChip(name).perform(clickListener())
        onView(chip(name)).check(matches(isChecked()))
        return this
    }

    fun assertCollectorChosen(name: String): CommentCreateRobot {
        awaitChip(name).check(matches(isChecked()))
        return this
    }

    /** Sin coleccionista elegido, sin valoración y sin texto, como al abrir la hoja por primera vez. */
    fun assertEmptyForm(collectorName: String): CommentCreateRobot {
        awaitChip(collectorName).check(matches(isNotChecked()))
        assertNoRating()
        assertComment("")
        return this
    }

    fun chooseRating(rating: Int): CommentCreateRobot {
        onView(withId(starId(rating))).perform(bringIntoView(), clickListener())
        return this
    }

    /** La etiqueta dice el valor y solo la estrella elegida queda seleccionada (no depende solo del color). */
    fun assertRating(rating: Int): CommentCreateRobot {
        val label = context.getString(R.string.comment_rating_value, rating)
        onView(withId(R.id.ratingLabel)).perform(bringIntoView()).check(matches(withText(label)))
        (1..STARS).forEach { star ->
            val state = if (star == rating) isSelected() else isNotSelected()
            onView(withId(starId(star))).check(matches(state))
        }
        return this
    }

    fun assertNoRating(): CommentCreateRobot {
        onView(withId(R.id.ratingLabel)).perform(bringIntoView()).check(matches(withText(R.string.comment_rating)))
        (1..STARS).forEach { star -> onView(withId(starId(star))).check(matches(isNotSelected())) }
        return this
    }

    fun typeComment(text: String): CommentCreateRobot {
        onView(withId(R.id.commentField)).perform(bringIntoView(), replaceText(text), closeSoftKeyboard())
        return this
    }

    fun assertComment(text: String): CommentCreateRobot {
        onView(withId(R.id.commentField)).check(matches(withText(text)))
        return this
    }

    fun tapPublish(): CommentCreateRobot = tap(R.id.publishComment)

    fun tapCancel(): CommentCreateRobot = tap(R.id.cancelComment)

    fun tapClose(): CommentCreateRobot = tap(R.id.closeSheet)

    fun assertErrors(
        collector: Boolean,
        rating: Boolean,
        description: Boolean,
    ): CommentCreateRobot {
        assertError(R.id.collectorError, collector)
        assertError(R.id.ratingError, rating)
        assertError(R.id.descriptionError, description)
        return this
    }

    fun assertNetworkError(): CommentCreateRobot {
        waitForDisplayed(allOf(withId(R.id.submitError), withText(R.string.comment_create_network_error)))
        return this
    }

    fun assertNoSubmitError(): CommentCreateRobot {
        assertError(R.id.submitError, shown = false)
        return this
    }

    // Polls without Espresso's failure handler: it takes a screenshot on every failed check, and on API 21 a run of
    // them freezes the emulator (Sprint 2, diferencias entre API 21 y 37, I2). The chip of a collector created by the
    // test arrives with the backend refresh, after the cached chips.
    private fun awaitChip(name: String): ViewInteraction {
        val deadline = System.currentTimeMillis() + CHIP_TIMEOUT_MILLIS
        while (true) {
            try {
                onView(chip(name)).withFailureHandler(rethrow).perform(bringIntoView()).check(matches(isDisplayed()))
                return onView(chip(name))
            } catch (error: NoMatchingViewException) {
                if (System.currentTimeMillis() > deadline) throw error
            } catch (error: AssertionError) {
                if (System.currentTimeMillis() > deadline) throw error
            }
            Thread.sleep(POLL_MILLIS)
        }
    }

    private fun chip(name: String): Matcher<View> = allOf(withText(name), isDescendantOfA(withId(R.id.collectorChips)))

    private fun tap(
        @IdRes viewId: Int,
    ): CommentCreateRobot {
        // En API 21 el centro visible del botón puede quedar fuera del toque de Espresso.
        onView(withId(viewId)).perform(bringIntoView(), clickListener())
        return this
    }

    private fun assertError(
        @IdRes viewId: Int,
        shown: Boolean,
    ) {
        val visibility = if (shown) Visibility.VISIBLE else Visibility.GONE
        onView(withId(viewId)).check(matches(withEffectiveVisibility(visibility)))
    }

    private fun starId(rating: Int): Int =
        when (rating) {
            1 -> R.id.star1
            2 -> R.id.star2
            3 -> R.id.star3
            4 -> R.id.star4
            5 -> R.id.star5
            else -> error("La valoración va de 1 a 5")
        }
}
