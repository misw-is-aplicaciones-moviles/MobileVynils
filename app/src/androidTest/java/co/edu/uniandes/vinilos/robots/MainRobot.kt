package co.edu.uniandes.vinilos.robots

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import co.edu.uniandes.vinilos.R

/**
 * Page Object de la pantalla inicial: reúne sus acciones y aserciones de Espresso.
 * Cada método devuelve el robot para encadenar llamadas; las historias agregan uno por pantalla en este paquete.
 */
class MainRobot {
    fun assertWelcomeShown(): MainRobot {
        onView(withId(R.id.welcome)).check(matches(isDisplayed()))
        onView(withId(R.id.welcome)).check(matches(withText(R.string.welcome)))
        return this
    }
}
