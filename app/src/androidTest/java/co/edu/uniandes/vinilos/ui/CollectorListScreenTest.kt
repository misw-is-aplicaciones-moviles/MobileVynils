package co.edu.uniandes.vinilos.ui

import androidx.navigation.fragment.NavHostFragment
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.robots.CollectorListRobot
import co.edu.uniandes.vinilos.testing.BackendCollectors
import co.edu.uniandes.vinilos.testing.BackendFirewall
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * Lista de coleccionistas contra BackVynils local (10.0.2.2:3000 con la semilla); un método por caso E2E del inventario
 * de docs/06. Cada caso crea sus propios coleccionistas, sin álbumes y con nombres únicos, y los borra al terminar. Las
 * cuentas de la semilla se contrastan con una lectura del backend en vez de fijarlas. El número único va en medio del
 * nombre para que las iniciales sean fijas.
 */
@RunWith(AndroidJUnit4::class)
class CollectorListScreenTest {
    private val data = BackendCollectors()
    private val firewall = BackendFirewall()
    private val activityRule = ActivityScenarioRule(MainActivity::class.java)

    // La limpieza de los datos se ejecuta al final: el corte de red ya se restauró y la actividad ya se cerró.
    @get:Rule val ruleChain: TestRule = RuleChain.outerRule(data).around(firewall).around(activityRule)

    private val collectors = CollectorListRobot()
    private val unique = System.currentTimeMillis()

    @Test
    fun visitor_sees_collectors_by_name_without_the_create_button() {
        val name = createCollector()
        val manolo = summaryOf(MANOLO_ID)
        val jaime = summaryOf(JAIME_ID)
        collectors.enterAs(R.string.role_visitor)

        collectors
            .assertRow(name, initials = "AP", email = EMAIL, summary = NO_ALBUMS)
            .assertRow("Manolo Bellon", initials = "MB", email = "manollo@caracol.com.co", summary = manolo)
            .assertRow("Jaime Monsalve", initials = "JM", email = "jmonsalve@rtvc.com.co", summary = jaime)
            .assertAlphabeticalOrder()
            .assertCreateButtonHidden()
    }

    // El catálogo le muestra el "+" a este rol: aquí se comprueba que no sigue visible al llegar a la pestaña.
    @Test
    fun collector_sees_the_same_list_and_no_create_button() {
        val name = createCollector()
        val manolo = summaryOf(MANOLO_ID)
        collectors.enterAs(R.string.role_collector)

        collectors
            .assertRow(name, initials = "AP", email = EMAIL, summary = NO_ALBUMS)
            .assertRow("Manolo Bellon", initials = "MB", email = "manollo@caracol.com.co", summary = manolo)
            .assertAlphabeticalOrder()
            .assertCreateButtonHidden()
    }

    @Test
    fun failing_load_without_saved_collectors_shows_the_error_and_retry_loads_the_list() {
        val name = createCollector()
        collectors.chooseRole(R.string.role_visitor)
        clearSavedCollectors()
        firewall.block()

        collectors.openCollectorsTab().assertErrorShown()

        firewall.restore()
        collectors.tapRetry().waitForList()
        collectors.assertRow(name, initials = "AP", email = EMAIL, summary = NO_ALBUMS)
    }

    @Test
    fun refreshing_without_connection_keeps_the_list_and_shows_the_offline_notice() {
        val name = createCollector()
        collectors.enterAs(R.string.role_visitor).scrollTo(name)
        firewall.block()

        collectors.pullToRefresh()

        waitForDisplayed(withText(R.string.collector_list_offline_notice))
        collectors.assertRow(name, initials = "AP", email = EMAIL)
    }

    @Test
    fun pull_to_refresh_shows_a_collector_added_on_the_backend() {
        val name = createCollector()
        collectors.enterAs(R.string.role_visitor).scrollTo(name)
        val added = "Nuevo $unique Agregado"
        data.createCollector(added, "nuevo@example.com")

        collectors
            .pullToRefresh()
            .assertRow(added, initials = "NA", email = "nuevo@example.com", summary = NO_ALBUMS)
    }

    @Test
    fun tapping_a_row_opens_the_detail_with_its_id() {
        val name = "Ana $unique Pardo"
        val id = data.createCollector(name, EMAIL)
        collectors.enterAs(R.string.role_visitor).tapRow(name)

        waitForDisplayed(withText(R.string.pending_next_sprint))
        activityRule.scenario.onActivity { activity ->
            val navHost = activity.supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
            val navController = navHost.navController
            val arguments = checkNotNull(navController.currentBackStackEntry?.arguments)
            assertEquals(R.id.collectorDetailFragment, navController.currentDestination?.id)
            assertEquals(id, arguments.getInt("collectorId"))
        }
    }

    private fun createCollector(): String {
        val name = "Ana $unique Pardo"
        data.createCollector(name, EMAIL)
        return name
    }

    /** La tercera línea que debe tener un coleccionista según lo que responde ahora el backend. */
    private fun summaryOf(collectorId: Int): String {
        val (albums, favorites) = data.countsOf(collectorId)
        if (albums == 0) return NO_ALBUMS
        val albumText = if (albums == 1) "1 álbum" else "$albums álbumes"
        val favoriteText = if (favorites == 1) "1 favorito" else "$favorites favoritos"
        return "$albumText · $favoriteText"
    }

    // Deja Room sin coleccionistas para que la carga siguiente no tenga nada que mostrar ni revalidar.
    private fun clearSavedCollectors() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as VinilosApplication).container
        runBlocking { container.collectorDao.replaceAll(emptyList()) }
    }

    private companion object {
        const val EMAIL = "ana.pardo@example.com"
        const val NO_ALBUMS = "Sin álbumes aún"
        const val MANOLO_ID = 100
        const val JAIME_ID = 101
    }
}
