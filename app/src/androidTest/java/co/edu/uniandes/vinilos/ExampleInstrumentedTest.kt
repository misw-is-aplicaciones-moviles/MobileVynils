package co.edu.uniandes.vinilos

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.robots.MainRobot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Prueba de humo: la app abre y muestra su pantalla inicial. Corre desde API 21. */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("co.edu.uniandes.vinilos", appContext.packageName)
    }

    @Test
    fun main_activity_shows_the_welcome_text() {
        MainRobot().assertWelcomeShown()
    }
}
