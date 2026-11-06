package co.edu.uniandes.vinilos.robots

import android.view.View
import androidx.test.espresso.matcher.BoundedMatcher
import com.google.android.material.textfield.TextInputLayout
import org.hamcrest.Description
import org.hamcrest.Matcher

/** El error de un campo empieza con un ícono, por eso se compara por el final del texto. */
internal fun hasErrorEndingWith(message: String): Matcher<View> =
    object : BoundedMatcher<View, TextInputLayout>(TextInputLayout::class.java) {
        override fun describeTo(description: Description) {
            description.appendText("campo con el error \"$message\"")
        }

        override fun matchesSafely(layout: TextInputLayout): Boolean =
            layout.error?.toString()?.endsWith(message) == true
    }

internal fun hasNoError(): Matcher<View> =
    object : BoundedMatcher<View, TextInputLayout>(TextInputLayout::class.java) {
        override fun describeTo(description: Description) {
            description.appendText("campo sin error")
        }

        override fun matchesSafely(layout: TextInputLayout): Boolean = layout.error == null
    }
