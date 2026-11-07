package co.edu.uniandes.vinilos.ui

import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.navigation.fragment.NavHostFragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.robots.CollectorDetailRobot
import co.edu.uniandes.vinilos.robots.CollectorListRobot
import co.edu.uniandes.vinilos.testing.BackendCollectors
import co.edu.uniandes.vinilos.testing.BackendFirewall
import co.edu.uniandes.vinilos.testing.CollectorSnapshot
import co.edu.uniandes.vinilos.testing.ListedCollector
import co.edu.uniandes.vinilos.testing.VinilosE2ERule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * Detalle de coleccionista contra BackVynils local. Un método por caso E2E del inventario de docs/06.
 * No crea coleccionistas ni afirma datos de la semilla: abre uno que ya devuelve `GET /collectors` y compara la
 * pantalla con `GET /collectors/:id` y `GET /collectors/:id/albums` de esa corrida.
 */
@RunWith(AndroidJUnit4::class)
class CollectorDetailScreenTest {
    private val data = BackendCollectors()
    private val firewall = BackendFirewall()
    private val activityRule = VinilosE2ERule(autoLaunch = true)

    @get:Rule val ruleChain: TestRule = RuleChain.outerRule(data).around(firewall).around(activityRule)

    private val collectors = CollectorListRobot()
    private val detail = CollectorDetailRobot()

    @Test
    fun visitor_sees_contact_counts_and_no_add_actions() {
        val collector = openCollector(R.string.role_visitor)

        detail
            .assertContact(collector)
            .assertCounts(collector)
            .assertAddActionsHidden()
            .assertCommentsHidden(collector.commentDescriptions)
        onView(withId(R.id.collectorListFragment)).check(matches(isSelected()))
    }

    @Test
    fun collector_sees_the_same_detail_with_the_album_action_enabled() {
        val collector = openCollector(R.string.role_collector)

        detail.assertContact(collector).assertCounts(collector).assertFavoriteDisabledAndAlbumEnabled()
    }

    @Test
    fun albums_section_matches_the_collectors_albums() {
        val collector = openCollector(R.string.role_visitor) { it.albumCount > 0 }

        detail.assertAlbums(collector.albums)
        collector.albums.firstOrNull()?.let { detail.openAlbum(it) }
    }

    @Test
    fun favorites_section_matches_the_collectors_favorites() {
        val collector = openCollector(R.string.role_visitor) { it.favoriteCount > 0 }

        detail.assertFavorites(collector.favorites)
        collector.favorites.firstOrNull()?.let { detail.openFavorite(it) }
    }

    @Test
    fun missing_collector_shows_not_found_and_returns_to_the_list() {
        collectors.enterAs(R.string.role_visitor)
        openMissingCollector(data.unusedCollectorId())

        detail.assertNotFound().tapBack()

        collectors.waitForList()
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.collector_list_title)))
        onView(withId(R.id.collectorListFragment)).check(matches(isSelected()))
    }

    @Test
    fun retry_after_a_network_failure_shows_the_collector() {
        val collector = collectorOnTheList()
        collectors.enterAs(R.string.role_visitor).scrollTo(collector.name)
        evictHttpCache()
        firewall.block()

        collectors.tapRow(collector.name)
        detail.assertNetworkError()

        firewall.restore()
        detail.tapRetry().waitUntilReady(collector.name).assertContact(readAgain(collector))
    }

    @Test
    fun recreating_the_screen_keeps_the_collector_and_reselecting_the_tab_returns_to_the_list() {
        val collector = openCollector(R.string.role_visitor)

        activityRule.scenario.recreate()

        detail.waitUntilReady(collector.name)
        collectors.openCollectorsTab().waitForList()
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.collector_list_title)))
        onView(withId(R.id.collectorListFragment)).check(matches(isSelected()))
    }

    private fun openCollector(
        @StringRes roleRes: Int,
        prefer: (ListedCollector) -> Boolean = { true },
    ): CollectorSnapshot {
        val collector = collectorOnTheList(prefer)
        collectors.enterAs(roleRes).tapRow(collector.name)
        val snapshot = data.readDetail(collector.id)
        detail.waitUntilReady(snapshot.name)
        return snapshot
    }

    /** Prefiere un coleccionista que cumpla [prefer] si hay alguno; si no, usa cualquiera de nombre único. */
    private fun collectorOnTheList(prefer: (ListedCollector) -> Boolean = { true }): ListedCollector {
        val listed = data.listedCollectors()
        val unique = listed.filter { candidate -> listed.count { it.name == candidate.name } == 1 }
        check(unique.isNotEmpty()) {
            "El backend no tiene un coleccionista con nombre único. La prueba no crea uno."
        }
        return unique.firstOrNull(prefer) ?: unique.first()
    }

    private fun readAgain(collector: ListedCollector): CollectorSnapshot = data.readDetail(collector.id)

    private fun openMissingCollector(id: Int) {
        activityRule.scenario.onActivity { activity ->
            val navHost = activity.supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
            navHost.navController.navigate(R.id.collectorDetailFragment, bundleOf("collectorId" to id))
        }
    }

    private fun evictHttpCache() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as VinilosApplication
        application.container.networkClient.httpClient.cache
            ?.evictAll()
    }
}
