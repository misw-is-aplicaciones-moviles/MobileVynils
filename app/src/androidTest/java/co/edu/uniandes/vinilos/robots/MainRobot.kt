package co.edu.uniandes.vinilos.robots

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import co.edu.uniandes.vinilos.R
import org.hamcrest.Matchers.allOf

/**
 * Page Object de la pantalla principal (cabecera y barra inferior): reúne sus acciones y aserciones de Espresso.
 * Cada método devuelve el robot para encadenar llamadas; las historias agregan uno por pantalla en este paquete.
 */
class MainRobot {
    fun assertCatalogShown(): MainRobot {
        // Cada pestaña tiene su cabecera; solo la visible cuenta.
        onView(allOf(withId(R.id.headerTitle), isDisplayed())).check(matches(withText(R.string.album_list_title)))
        return this
    }
}
