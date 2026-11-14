package co.edu.uniandes.vinilos.robots

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Matchers.allOf

/** Page Object de la hoja de precio y estado. El precio se arma en el teclado propio, de izquierda a derecha. */
class CollectorAlbumAddRobot {
    fun assertOpen(
        albumName: String,
        collectorName: String,
    ): CollectorAlbumAddRobot {
        onView(withId(R.id.sheetTitle)).perform(scrollTo()).check(
            matches(allOf(isDisplayed(), withText(R.string.collector_album_add_title))),
        )
        onView(withId(R.id.sheetSubtitle)).check(matches(withText("$albumName · $collectorName")))
        return this
    }

    fun assertPrice(text: String): CollectorAlbumAddRobot {
        onView(withId(R.id.priceDisplay)).perform(scrollTo()).check(matches(withText(text)))
        return this
    }

    fun enterDigits(vararg digits: Int): CollectorAlbumAddRobot {
        digits.forEach { digit -> onView(withId(keyId(digit))).perform(scrollTo(), click()) }
        return this
    }

    fun tapDelete(): CollectorAlbumAddRobot = tap(R.id.keyDelete)

    fun tapClear(): CollectorAlbumAddRobot = tap(R.id.keyClear)

    fun chooseAvailable(): CollectorAlbumAddRobot = tap(R.id.statusAvailable)

    fun chooseUnavailable(): CollectorAlbumAddRobot = tap(R.id.statusUnavailable)

    fun tapAdd(): CollectorAlbumAddRobot = tap(R.id.addButton)

    fun tapCancel(): CollectorAlbumAddRobot = tap(R.id.cancelButton)

    fun tapClose(): CollectorAlbumAddRobot = tap(R.id.closeSheet)

    fun assertPriceError(shown: Boolean): CollectorAlbumAddRobot =
        assertError(R.id.priceError, R.string.collector_album_add_price_error, shown)

    fun assertStatusError(shown: Boolean): CollectorAlbumAddRobot =
        assertError(R.id.statusError, R.string.collector_album_add_status_error, shown)

    fun assertSubmitEnabled(): CollectorAlbumAddRobot {
        onView(withId(R.id.addButton)).perform(scrollTo()).check(matches(allOf(isDisplayed(), isEnabled())))
        return this
    }

    fun assertNetworkError(): CollectorAlbumAddRobot {
        waitForDisplayed(withText(R.string.collector_album_add_network_error))
        return this
    }

    private fun tap(
        @IdRes viewId: Int,
    ): CollectorAlbumAddRobot {
        onView(withId(viewId)).perform(scrollTo(), click())
        return this
    }

    private fun assertError(
        @IdRes viewId: Int,
        @StringRes text: Int,
        shown: Boolean,
    ): CollectorAlbumAddRobot {
        val visibility = if (shown) Visibility.VISIBLE else Visibility.GONE
        onView(withId(viewId)).check(matches(withEffectiveVisibility(visibility)))
        if (shown) {
            // El aviso es un solo TextView con el ícono como compound drawable (Lint UseCompoundDrawables).
            onView(allOf(withId(viewId), withText(text))).check(matches(isDisplayed()))
        }
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
