package co.edu.uniandes.vinilos

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.robots.MainRobot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Prueba de humo: la app abre en el catálogo. Corre desde API 21. */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("co.edu.uniandes.vinilos", appContext.packageName)
    }

    @Test
    fun app_opens_on_the_catalog_tab() {
        MainRobot().assertCatalogShown()
    }
}
