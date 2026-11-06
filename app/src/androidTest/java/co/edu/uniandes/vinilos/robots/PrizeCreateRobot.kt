package co.edu.uniandes.vinilos.robots

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.not

/** Page Object del formulario de nuevo premio (HU13). Los errores empiezan con un ícono: se comparan por el final. */
class PrizeCreateRobot {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    fun assertFormShown(): PrizeCreateRobot {
        waitForDisplayed(allOf(withText(R.string.create_prize_all_required), isDisplayed()))
        waitForDisplayed(allOf(withId(R.id.headerTitle), withText(R.string.create_prize_title)))
        return this
    }

    fun typeName(name: String) = type(R.id.prizeNameInput, name)

    fun typeDescription(description: String) = type(R.id.prizeDescriptionInput, description)

    fun typeOrganization(organization: String) = type(R.id.prizeOrganizationInput, organization)

    fun tapCreate(): PrizeCreateRobot {
        onView(withId(R.id.createPrizeButton)).perform(click())
        return this
    }

    fun tapCancel(): PrizeCreateRobot {
        onView(withId(R.id.cancelPrizeButton)).perform(click())
        return this
    }

    fun tapClose(): PrizeCreateRobot {
        onView(allOf(withId(R.id.headerClose), isDisplayed())).perform(click())
        return this
    }

    fun assertFieldError(
        @IdRes error: Int,
        @StringRes message: Int,
    ): PrizeCreateRobot {
        onView(withId(error)).check(matches(allOf(isDisplayed(), withText(endsWith(context.getString(message))))))
        return this
    }

    fun assertNoFieldError(
        @IdRes error: Int,
    ): PrizeCreateRobot {
        onView(withId(error)).check(matches(not(isDisplayed())))
        return this
    }

    fun assertText(
        @IdRes input: Int,
        text: String,
    ): PrizeCreateRobot {
        onView(withId(input)).check(matches(withText(text)))
        return this
    }

    /** Espera el texto del botón principal: "Crear premio", "Creando…", "Comprobar"... */
    fun waitForPrimaryAction(
        @StringRes label: Int,
    ): PrizeCreateRobot {
        waitForDisplayed(allOf(withId(R.id.createPrizeButton), withText(label)))
        return this
    }

    fun assertFieldsLocked(locked: Boolean): PrizeCreateRobot {
        listOf(R.id.prizeNameInput, R.id.prizeDescriptionInput, R.id.prizeOrganizationInput).forEach { input ->
            onView(withId(input)).check(matches(if (locked) not(isEnabled()) else isEnabled()))
        }
        return this
    }

    fun waitForNotice(
        @StringRes message: Int,
    ): PrizeCreateRobot {
        waitForDisplayed(allOf(withId(R.id.formNotice), withText(message)))
        return this
    }

    private fun type(
        @IdRes input: Int,
        text: String,
    ): PrizeCreateRobot {
        onView(withId(input)).perform(scrollTo(), replaceText(text), closeSoftKeyboard())
        return this
    }
}
