package co.edu.uniandes.vinilos.robots

import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Matchers.allOf

/** Page Object de la hoja "Nueva canción". La duración se marca en el teclado propio, de derecha a izquierda. */
class TrackCreateRobot {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    fun assertOpen(): TrackCreateRobot {
        waitForDisplayed(withId(R.id.sheetTitle)).check(matches(withText(R.string.track_sheet_title)))
        return this
    }

    fun assertSubtitle(
        albumName: String,
        number: Int,
    ): TrackCreateRobot {
        val subtitle = context.getString(R.string.track_sheet_subtitle, albumName, number)
        onView(withId(R.id.sheetSubtitle)).check(matches(withText(subtitle)))
        return this
    }

    fun typeName(name: String): TrackCreateRobot {
        onView(withId(R.id.trackNameField)).perform(scrollTo(), replaceText(name), closeSoftKeyboard())
        return this
    }

    fun assertName(name: String): TrackCreateRobot {
        onView(withId(R.id.trackNameField)).check(matches(withText(name)))
        return this
    }

    fun enterDigits(vararg digits: Int): TrackCreateRobot {
        digits.forEach { digit -> onView(withId(keyId(digit))).perform(scrollTo(), click()) }
        return this
    }

    fun assertDuration(text: String): TrackCreateRobot {
        onView(allOf(withId(R.id.trackDuration), isDisplayed())).check(matches(withText(text)))
        return this
    }

    fun tapClear(): TrackCreateRobot = tap(R.id.keyClear)

    fun tapDelete(): TrackCreateRobot = tap(R.id.keyDelete)

    fun tapSave(): TrackCreateRobot = tap(R.id.saveTrack)

    fun tapCancel(): TrackCreateRobot = tap(R.id.cancelTrack)

    fun tapClose(): TrackCreateRobot = tap(R.id.closeSheet)

    fun assertNameErrorShown(): TrackCreateRobot = assertError(R.id.nameError, shown = true)

    fun assertNameErrorHidden(): TrackCreateRobot = assertError(R.id.nameError, shown = false)

    fun assertDurationErrorShown(): TrackCreateRobot = assertError(R.id.durationError, shown = true)

    fun assertDurationErrorHidden(): TrackCreateRobot = assertError(R.id.durationError, shown = false)

    fun assertNetworkError(): TrackCreateRobot {
        waitForDisplayed(withText(R.string.track_create_network_error))
        return this
    }

    private fun tap(
        @IdRes viewId: Int,
    ): TrackCreateRobot {
        onView(withId(viewId)).perform(scrollTo(), click())
        return this
    }

    private fun assertError(
        @IdRes viewId: Int,
        shown: Boolean,
    ): TrackCreateRobot {
        val visibility = if (shown) Visibility.VISIBLE else Visibility.GONE
        onView(withId(viewId)).check(matches(withEffectiveVisibility(visibility)))
        return this
    }

    private fun keyId(digit: Int): Int =
        when (digit) {
            0 -> R.id.key0
            1 -> R.id.key1
            2 -> R.id.key2
            3 -> R.id.key3
            4 -> R.id.key4
            5 -> R.id.key5
            6 -> R.id.key6
            7 -> R.id.key7
            8 -> R.id.key8
            9 -> R.id.key9
            else -> error("El teclado solo tiene los dígitos 0 a 9")
        }
}
