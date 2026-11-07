package co.edu.uniandes.vinilos.ui

import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.robots.AlbumListRobot
import co.edu.uniandes.vinilos.robots.CollectorDetailRobot
import co.edu.uniandes.vinilos.robots.CollectorListRobot
import co.edu.uniandes.vinilos.robots.PerformerDetailRobot
import co.edu.uniandes.vinilos.robots.PerformerListRobot
import co.edu.uniandes.vinilos.testing.BackendCollectors
import co.edu.uniandes.vinilos.testing.BackendPerformers
import co.edu.uniandes.vinilos.testing.CollectorSnapshot
import co.edu.uniandes.vinilos.testing.VinilosE2ERule
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * Tocar una pestaña lleva siempre a su lista, aunque el detalle abierto pertenezca a otra pestaña.
 * Los coleccionistas se leen del backend sin escribir; los artistas y sus álbumes los crea cada prueba.
 */
@RunWith(AndroidJUnit4::class)
class TabNavigationTest {
    private val collectorData = BackendCollectors()
    private val performerData = BackendPerformers()
    private val activityRule = VinilosE2ERule(autoLaunch = true)

    @get:Rule val ruleChain: TestRule =
        RuleChain.outerRule(collectorData).around(performerData).around(activityRule)

    private val collectors = CollectorListRobot()
    private val collectorDetail = CollectorDetailRobot()
    private val artists = PerformerListRobot()
    private val name = "Pestañas ${System.nanoTime()}"
    private val albumName = "$name álbum 1"

    @Test
    fun album_opened_from_a_collector_returns_to_the_collector_list() {
        val collector = openCollectorWithAlbumAndFavorite()
        collectorDetail.openAlbum(collector.albums.first())

        tapTab(R.id.collectorListFragment)

        assertOnCollectorList()
    }

    @Test
    fun favorite_opened_from_a_collector_returns_to_the_collector_list() {
        val collector = openCollectorWithAlbumAndFavorite()
        collectorDetail.assertFavorites(collector.favorites).openFavorite(collector.favorites.first())

        tapTab(R.id.collectorListFragment)

        assertOnCollectorList()
    }

    @Test
    fun marked_tab_opens_its_list_and_the_previous_tab_does_not_bring_the_detail_back() {
        val collector = openCollectorWithAlbumAndFavorite()
        collectorDetail.assertFavorites(collector.favorites).openFavorite(collector.favorites.first())

        tapTab(R.id.performerListFragment)
        assertOnArtistList()

        tapTab(R.id.collectorListFragment)
        assertOnCollectorList()
    }

    @Test
    fun album_opened_from_an_artist_returns_to_the_artist_list() {
        performerData.createMusician(name, albumCount = 1)
        artists.enterAs(R.string.role_visitor).tapRow(name)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(name)))
        PerformerDetailRobot().scrollToText(albumName)
        onView(allOf(withId(R.id.albumTitle), withText(albumName))).perform(click())
        waitForDisplayed(allOf(withId(R.id.albumName), withText(albumName)))

        tapTab(R.id.performerListFragment)

        assertOnArtistList()
    }

    @Test
    fun artist_opened_from_an_album_returns_to_the_catalog() {
        performerData.createMusician(name, albumCount = 1)
        artists.chooseRole(R.string.role_visitor)
        openAlbumFromCatalog()
        PerformerDetailRobot().scrollToText(name)
        onView(withId(R.id.artistLink)).perform(click())
        waitForDisplayed(allOf(withId(R.id.performerName), withText(name)))

        tapTab(R.id.albumListFragment)

        assertOnCatalog()
    }

    @Test
    fun albums_tab_opens_the_catalog_after_visiting_the_other_tabs() {
        val collector = openCollectorWithAlbumAndFavorite()
        collectorDetail.waitUntilReady(collector.name)

        tapTab(R.id.albumListFragment)
        assertOnCatalog()

        tapTab(R.id.performerListFragment)
        assertOnArtistList()
        tapTab(R.id.collectorListFragment)
        assertOnCollectorList()
        tapTab(R.id.albumListFragment)
        assertOnCatalog()
    }

    @Test
    fun artist_list_keeps_its_filter_when_returning_to_the_tab() {
        performerData.createBand(name)
        artists
            .enterAs(R.string.role_visitor)
            .select(R.id.filterBands)
            .assertSelected(R.id.filterBands)
            .tapRow(name)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(name)))

        tapTab(R.id.collectorListFragment)
        assertOnCollectorList()
        tapTab(R.id.performerListFragment)

        assertOnArtistList()
        artists.assertSelected(R.id.filterBands)
    }

    @Test
    fun back_from_an_album_still_returns_to_the_collector() {
        val collector = openCollectorWithAlbumAndFavorite()
        collectorDetail.openAlbum(collector.albums.first())

        pressBack()

        collectorDetail.waitUntilReady(collector.name)
        assertDestination(R.id.collectorDetailFragment)
        assertTabSelected(R.id.collectorListFragment)
    }

    @Test
    fun primary_action_follows_the_screen_when_leaving_an_album_as_collector() {
        performerData.createMusician(name, albumCount = 1)
        artists.chooseRole(R.string.role_collector)
        openAlbumFromCatalog()
        waitForDisplayed(allOf(withId(R.id.primaryAction), isDisplayed()))

        tapTab(R.id.performerListFragment)
        assertOnArtistList()
        onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())))

        tapTab(R.id.albumListFragment)
        assertOnCatalog()
        waitForDisplayed(allOf(withId(R.id.primaryAction), isDisplayed()))

        openAlbumFromCatalog()
        tapTab(R.id.collectorListFragment)
        assertOnCollectorList()
        onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())))
    }

    private fun openCollectorWithAlbumAndFavorite(): CollectorSnapshot {
        val listed = collectorData.listedCollectors()
        val collector =
            listed.firstOrNull { candidate ->
                candidate.albumCount > 0 &&
                    candidate.favoriteCount > 0 &&
                    listed.count { it.name == candidate.name } == 1
            }
        checkNotNull(collector) {
            "El backend no tiene un coleccionista de nombre único con álbumes y favoritos. La prueba no crea uno."
        }
        collectors.enterAs(R.string.role_visitor).tapRow(collector.name)
        val snapshot = collectorData.readDetail(collector.id)
        collectorDetail.waitUntilReady(snapshot.name)
        return snapshot
    }

    private fun openAlbumFromCatalog() {
        AlbumListRobot().scrollTo(albumName)
        onView(withId(R.id.albumList)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(hasDescendant(withText(albumName)), click()),
        )
        waitForDisplayed(allOf(withId(R.id.albumName), withText(albumName)))
    }

    private fun tapTab(
        @IdRes tabId: Int,
    ) {
        onView(tab(tabId)).perform(click())
    }

    private fun assertOnCollectorList() =
        assertOnList(R.id.collectorList, R.string.collector_list_title, R.id.collectorListFragment)

    private fun assertOnArtistList() =
        assertOnList(R.id.performerList, R.string.artist_list_title, R.id.performerListFragment)

    private fun assertOnCatalog() = assertOnList(R.id.albumList, R.string.album_list_title, R.id.albumListFragment)

    private fun assertOnList(
        @IdRes listId: Int,
        @StringRes title: Int,
        @IdRes destinationId: Int,
    ) {
        waitForDisplayed(allOf(withId(listId), isDisplayed()))
        waitForDisplayed(allOf(withId(R.id.headerTitle), withText(title), isDisplayed()))
        assertDestination(destinationId)
        assertTabSelected(destinationId)
    }

    private fun assertDestination(
        @IdRes destinationId: Int,
    ) {
        activityRule.scenario.onActivity { activity ->
            val navHost = activity.supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
            assertEquals(destinationId, navHost.navController.currentDestination?.id)
        }
    }

    private fun assertTabSelected(
        @IdRes tabId: Int,
    ) {
        waitForDisplayed(allOf(tab(tabId), isSelected()))
    }

    private fun tab(
        @IdRes tabId: Int,
    ): Matcher<View> = allOf(withId(tabId), isDescendantOfA(withId(R.id.bottomNav)))
}
