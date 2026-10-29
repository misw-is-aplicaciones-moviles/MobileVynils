package co.edu.uniandes.vinilos.robots

import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import com.google.android.material.textfield.TextInputLayout
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.endsWith

/**
 * Page Object del formulario de nuevo álbum. Los mensajes de error empiezan con un ícono,
 * por eso se comparan por el final del texto.
 */
class AlbumCreateRobot {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    fun assertFormShown(): AlbumCreateRobot {
        waitForDisplayed(withText(R.string.create_album_all_required))
        return this
    }

    fun typeName(name: String) = type(R.id.nameInput, name)

    fun typeCoverUrl(url: String) = type(R.id.coverInput, url)

    fun typeDescription(description: String) = type(R.id.descriptionInput, description)

    /** Abre el calendario y acepta el día que propone (hoy). */
    fun pickReleaseDate(): AlbumCreateRobot {
        onView(withId(R.id.dateInput)).perform(scrollTo(), click())
        onView(withText(R.string.action_accept)).perform(click())
        return this
    }

    fun selectGenre(genre: String) = selectChip(genre)

    fun selectRecordLabel(recordLabel: String) = selectChip(recordLabel)

    fun tapCreate(): AlbumCreateRobot {
        onView(withId(R.id.createButton)).perform(click())
        return this
    }

    fun tapCancel(): AlbumCreateRobot {
        onView(withId(R.id.cancelButton)).perform(click())
        return this
    }

    fun tapClose(): AlbumCreateRobot {
        onView(allOf(withId(R.id.headerClose), isDisplayed())).perform(click())
        return this
    }

    fun assertFieldError(
        @IdRes layout: Int,
        @StringRes message: Int,
    ): AlbumCreateRobot {
        onView(withId(layout)).check(matches(hasErrorEndingWith(context.getString(message))))
        return this
    }

    fun assertNoFieldError(
        @IdRes layout: Int,
    ): AlbumCreateRobot {
        onView(withId(layout)).check(matches(hasNoError()))
        return this
    }

    fun assertChoiceError(
        @IdRes errorView: Int,
        @StringRes message: Int,
    ): AlbumCreateRobot {
        // Visible aunque quede bajo el borde del scroll: isDisplayed() exigiría que esté en pantalla.
        onView(withId(errorView))
            .check(
                matches(
                    allOf(withEffectiveVisibility(Visibility.VISIBLE), withText(endsWith(context.getString(message)))),
                ),
            )
        return this
    }

    fun assertNoChoiceError(
        @IdRes errorView: Int,
    ): AlbumCreateRobot {
        onView(withId(errorView)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        return this
    }

    fun assertText(
        @IdRes input: Int,
        text: String,
    ): AlbumCreateRobot {
        onView(withId(input)).check(matches(withText(text)))
        return this
    }

    private fun type(
        @IdRes input: Int,
        text: String,
    ): AlbumCreateRobot {
        onView(withId(input)).perform(scrollTo(), replaceText(text), closeSoftKeyboard())
        return this
    }

    private fun selectChip(label: String): AlbumCreateRobot {
        onView(withText(label)).perform(scrollTo(), click())
        return this
    }

    private fun hasErrorEndingWith(message: String): Matcher<View> =
        object : BoundedMatcher<View, TextInputLayout>(TextInputLayout::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("campo con el error \"$message\"")
            }

            override fun matchesSafely(layout: TextInputLayout): Boolean =
                layout.error?.toString()?.endsWith(message) == true
        }

    private fun hasNoError(): Matcher<View> =
        object : BoundedMatcher<View, TextInputLayout>(TextInputLayout::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("campo sin error")
            }

            override fun matchesSafely(layout: TextInputLayout): Boolean = layout.error == null
        }
}
