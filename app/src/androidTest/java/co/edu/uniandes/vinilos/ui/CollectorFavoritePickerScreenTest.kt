package co.edu.uniandes.vinilos.ui

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.robots.AlbumListRobot
import co.edu.uniandes.vinilos.robots.CollectorDetailRobot
import co.edu.uniandes.vinilos.robots.CollectorFavoritePickerRobot
import co.edu.uniandes.vinilos.robots.CollectorListRobot
import co.edu.uniandes.vinilos.robots.PerformerListRobot
import co.edu.uniandes.vinilos.testing.BackendCollectorFavorites
import co.edu.uniandes.vinilos.testing.BackendFirewall
import co.edu.uniandes.vinilos.testing.FavoriteKind
import co.edu.uniandes.vinilos.testing.FavoriteSnapshot
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import co.edu.uniandes.vinilos.testing.waitUntilGone
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

private const val POLL_MILLIS = 200L
private const val SAVED_TIMEOUT_MILLIS = 15_000L

/**
 * Agregar un artista favorito a un coleccionista contra BackVynils local. Un método por caso E2E de HU10.
 * El coleccionista, el músico y la banda son de esa ejecución. La limpieza borra los artistas y después el coleccionista.
 */
@RunWith(AndroidJUnit4::class)
class CollectorFavoritePickerScreenTest {
    private val data = BackendCollectorFavorites()
    private val firewall = BackendFirewall()
    private val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule val ruleChain: TestRule = RuleChain.outerRule(data).around(firewall).around(activityRule)

    private val catalogs = AlbumListRobot()
    private val performers = PerformerListRobot()
    private val collectors = CollectorListRobot()
    private val detail = CollectorDetailRobot()
    private val picker = CollectorFavoritePickerRobot()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun adds_a_musician_and_then_a_band_on_the_same_collector() {
        val name = collectorName()
        val collector = data.createCollector(name, email())
        val musician = musicianName()
        val band = bandName()
        val musicianId = data.createMusician(musician, MUSICIAN_BIRTH)
        val bandId = data.createBand(band, BAND_CREATION)
        openCollector(name, musician, band)

        detail.assertAddActionsEnabled()
        addFavorite(name, musician, musicianOverline(), count = 1)
        detail.assertFavorites(listOf(FavoriteSnapshot(musician, FavoriteKind.MUSICIAN)))

        addFavorite(name, band, bandOverline(), count = 2)
        detail.assertFavorites(
            listOf(
                FavoriteSnapshot(musician, FavoriteKind.MUSICIAN),
                FavoriteSnapshot(band, FavoriteKind.BAND),
            ),
        )
        assertFavoriteIds(collector, musicianId, bandId)
    }

    @Test
    fun already_favorite_musician_stays_blocked() {
        val name = collectorName()
        val collector = data.createCollector(name, email())
        val musician = musicianName()
        val musicianId = data.createMusician(musician, MUSICIAN_BIRTH)
        val band = bandName()
        data.createBand(band, BAND_CREATION)
        data.linkMusician(collector, musicianId)
        openCollector(name, musician, band)

        detail.tapAddFavorite()
        picker
            .waitUntilOpen(name)
            .search(musician)
            .assertAlreadyAdded(musician)
            .assertSelectPrompt()

        assertFavoriteIds(collector, musicianId)
    }

    @Test
    fun search_with_no_hits_then_the_musician_and_close_writes_nothing() {
        val name = collectorName()
        val collector = data.createCollector(name, email())
        val musician = musicianName()
        val band = bandName()
        data.createMusician(musician, MUSICIAN_BIRTH)
        data.createBand(band, BAND_CREATION)
        openCollector(name, musician, band)

        detail.tapAddFavorite()
        picker
            .waitUntilOpen(name)
            .search(missQuery())
            .assertNoResults()
            .search(musician.lowercase())
            .assertOnly(musician)
            .tapClose()

        detail.waitUntilReady(name)
        onView(withText(R.string.collector_favorite_added)).check(doesNotExist())
        assertFavoriteIds(collector)
    }

    @Test
    fun network_failure_keeps_the_picker_and_a_retry_saves_once() {
        val name = collectorName()
        val collector = data.createCollector(name, email())
        val musician = musicianName()
        val musicianId = data.createMusician(musician, MUSICIAN_BIRTH)
        val band = bandName()
        data.createBand(band, BAND_CREATION)
        openCollector(name, musician, band)

        detail.tapAddFavorite()
        picker
            .waitUntilOpen(name)
            .search(musician)
            .select(musician)
            .assertAdd(musician)
        firewall.block()
        picker
            .tapAdd()
            .assertSubmitNetworkError()
            .waitUntilOpen(name)
            .assertAdd(musician)
        // El GET sale por HttpURLConnection y el firewall también lo desvía.
        firewall.restore()
        assertFavoriteIds(collector)
        picker.tapAdd()
        awaitSaved(count = 1)
        dismissNotice()
        detail.assertFavorites(listOf(FavoriteSnapshot(musician, FavoriteKind.MUSICIAN)))
        assertFavoriteIds(collector, musicianId)
    }

    @Test
    fun picker_network_error_recovers_and_selection_survives_recreation() {
        val name = collectorName()
        val collector = data.createCollector(name, email())
        val musician = musicianName()
        val band = bandName()
        data.createMusician(musician, MUSICIAN_BIRTH)
        data.createBand(band, BAND_CREATION)
        openCollector(name, musician, band)

        firewall.block()
        detail.tapAddFavorite()
        picker.assertLoadError()
        firewall.restore()
        picker
            .tapRetry()
            .waitUntilOpen(name)
            .search(musician)
            .select(musician)
            .assertAdd(musician)

        activityRule.scenario.recreate()

        picker.assertSearch(musician).assertAdd(musician).tapClose()
        detail.waitUntilReady(name)
        onView(withText(R.string.collector_favorite_added)).check(doesNotExist())
        assertFavoriteIds(collector)
    }

    private fun addFavorite(
        collectorName: String,
        artist: String,
        overline: String,
        count: Int,
    ) {
        detail.tapAddFavorite()
        picker
            .waitUntilOpen(collectorName)
            .search(artist)
            .assertRow(artist, overline)
            .select(artist)
            .assertAdd(artist)
            .tapAdd()
        awaitSaved(count)
        dismissNotice()
    }

    private fun openCollector(
        name: String,
        vararg artists: String,
    ) {
        catalogs.enterAs(R.string.role_collector)
        performers.openArtistsTab().waitForList().pullToRefresh()
        artists.forEach { performers.scrollTo(it) }
        collectors
            .openCollectorsTab()
            .waitForList()
            .pullToRefresh()
            .tapRow(name)
        detail.waitUntilReady(name)
    }

    // El aviso dura poco y el detalle se recarga al guardar. La sección «Artistas favoritos» permanece.
    private fun awaitSaved(count: Int) {
        val notice = withText(R.string.collector_favorite_added)
        val savedSection =
            allOf(
                withId(R.id.sectionTitle),
                withText(context.getString(R.string.collector_section_favorites, count)),
            )
        val deadline = System.currentTimeMillis() + SAVED_TIMEOUT_MILLIS
        var last: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            last = displayFailure(notice)
            if (last == null) return
            val sectionMissing = displayFailure(savedSection)
            if (sectionMissing == null) return
            last = sectionMissing
            Thread.sleep(POLL_MILLIS)
        }
        fail("El favorito guardado no apareció en $SAVED_TIMEOUT_MILLIS ms ($last)")
    }

    private fun dismissNotice() {
        val notice = withText(R.string.collector_favorite_added)
        if (displayFailure(notice) == null) waitUntilGone(notice)
    }

    private fun displayFailure(matcher: Matcher<View>): Throwable? =
        try {
            onView(matcher).check(matches(isDisplayed()))
            null
        } catch (missing: NoMatchingViewException) {
            missing
        } catch (hidden: AssertionError) {
            hidden
        }

    private fun assertFavoriteIds(
        collectorId: Int,
        vararg ids: Int,
    ) {
        val actual = data.favoriteIds(collectorId)
        assertEquals(ids.toSet(), actual.toSet())
        assertEquals(ids.size, actual.size)
    }

    private fun musicianOverline(): String =
        context.getString(
            R.string.artist_overline,
            context.getString(R.string.artist_musician),
            MUSICIAN_YEAR.toString(),
        )

    private fun bandOverline(): String =
        context.getString(
            R.string.artist_overline,
            context.getString(R.string.artist_band),
            context.getString(R.string.artist_since, BAND_YEAR),
        )

    private fun collectorName() = "Coleccionista ${token()}"

    private fun musicianName() = "Musico ${token()}"

    private fun bandName() = "Banda ${token()}"

    private fun email() = "f${token()}@example.com"

    private fun missQuery() = "qqq${token()}nomatch"

    private fun token() = System.nanoTime().toString()

    private companion object {
        const val MUSICIAN_YEAR = 1947
        const val BAND_YEAR = 1970
        const val MUSICIAN_BIRTH = "1947-07-19T00:00:00.000Z"
        const val BAND_CREATION = "1970-01-01T00:00:00.000Z"
    }
}
