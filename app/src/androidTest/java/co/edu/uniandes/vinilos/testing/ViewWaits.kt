package co.edu.uniandes.vinilos.testing

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import org.hamcrest.Matcher
import org.junit.Assert.fail

private const val POLL_MILLIS = 100L
private const val DEFAULT_TIMEOUT_MILLIS = 15_000L

/**
 * Espera a que [matcher] aparezca en pantalla. Las lecturas de Room y de red corren en corrutinas que Espresso no
 * ve, así que las pruebas sondean en vez de depender de un recurso inactivo.
 */
fun waitForDisplayed(
    matcher: Matcher<View>,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
): ViewInteraction {
    val deadline = System.currentTimeMillis() + timeoutMillis
    var last: Throwable? = null
    while (System.currentTimeMillis() < deadline) {
        try {
            return onView(matcher).check(matches(isDisplayed()))
        } catch (error: NoMatchingViewException) {
            last = error
        } catch (error: AssertionError) {
            last = error
        }
        Thread.sleep(POLL_MILLIS)
    }
    fail("The view did not appear within $timeoutMillis ms: $matcher ($last)")
    error("unreachable")
}

/** Espera a que [matcher] salga de la jerarquía, por ejemplo al cerrar la hoja. */
fun waitUntilGone(
    matcher: Matcher<View>,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    var last: Throwable? = null
    while (System.currentTimeMillis() < deadline) {
        var absent = false
        try {
            onView(matcher).check(doesNotExist())
            absent = true
        } catch (error: NoMatchingViewException) {
            last = error
            absent = true
        } catch (error: AssertionError) {
            last = error
        }
        if (absent) return
        Thread.sleep(POLL_MILLIS)
    }
    fail("The view was still present after $timeoutMillis ms: $matcher ($last)")
}
