package co.edu.uniandes.vinilos.ui

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.robots.AlbumListRobot
import co.edu.uniandes.vinilos.robots.CollectorAlbumAddRobot
import co.edu.uniandes.vinilos.robots.CollectorAlbumPickerRobot
import co.edu.uniandes.vinilos.robots.CollectorDetailRobot
import co.edu.uniandes.vinilos.robots.CollectorListRobot
import co.edu.uniandes.vinilos.testing.AlbumSnapshot
import co.edu.uniandes.vinilos.testing.BackendCollectorAlbums
import co.edu.uniandes.vinilos.testing.BackendFirewall
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import co.edu.uniandes.vinilos.testing.waitUntilGone
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

private const val POLL_MILLIS = 200L
private const val CATALOG_TIMEOUT_MILLIS = 15_000L
private const val SAMPLE_PRICE = 150
private const val MINIMUM_PRICE = 1
private const val MAXIMUM_PRICE = 999_999
private const val PREPARED_PRICE = 20
private const val CLEARED_PRICE = 12
private const val ACTIVE = "Active"
private const val INACTIVE = "Inactive"
private const val ROCK = "Rock"
private const val FOLK = "Folk"

/**
 * Agregar un álbum a un coleccionista contra BackVynils local. Un método por caso E2E del inventario de docs/06.
 * El coleccionista y los álbumes son de esa ejecución. La limpieza borra solo esos vínculos.
 */
@RunWith(AndroidJUnit4::class)
class CollectorAlbumPickerScreenTest {
    private val data = BackendCollectorAlbums()
    private val firewall = BackendFirewall()
    private val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule val ruleChain: TestRule = RuleChain.outerRule(data).around(firewall).around(activityRule)

    private val catalogs = AlbumListRobot()
    private val collectors = CollectorListRobot()
    private val detail = CollectorDetailRobot()
    private val picker = CollectorAlbumPickerRobot()
    private val sheet = CollectorAlbumAddRobot()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun adds_an_available_album_and_shows_it_on_the_detail() {
        val name = collectorName()
        val collector = data.createCollector(name, email())
        val album = albumName("disponible")
        data.createAlbum(album)
        openCollector(name, album)

        detail.assertFavoriteDisabledAndAlbumEnabled().tapAddAlbum()
        picker
            .waitUntilOpen(name)
            .assertRow(album, genreMeta(ROCK))
            .select(album)
            .assertContinue(album)
        picker.tapContinue()
        sheet.assertOpen(album, name).enterDigits(*digitsOf(SAMPLE_PRICE)).assertPrice(sheetPrice(SAMPLE_PRICE))
        sheet.chooseAvailable().tapAdd()

        waitForDisplayed(withText(context.getString(R.string.collector_album_selected, album)))
        assertOnDetail(album, count = 1, price = SAMPLE_PRICE, status = ACTIVE)
        assertLink(collector, album, SAMPLE_PRICE, ACTIVE)
    }

    @Test
    fun accepts_the_minimum_price_and_the_maximum_for_both_statuses() {
        val name = collectorName()
        val collector = data.createCollector(name, email())
        val minimum = albumName("minimo")
        val maximum = albumName("tope")
        data.createAlbum(minimum)
        data.createAlbum(maximum)
        openCollector(name, minimum, maximum)

        add(name, minimum, intArrayOf(MINIMUM_PRICE), unavailable = false, price = MINIMUM_PRICE)
        assertOnDetail(minimum, count = 1, price = MINIMUM_PRICE, status = ACTIVE)

        add(
            name,
            maximum,
            IntArray(DIGITS_PAST_THE_MAXIMUM) { MAXIMUM_DIGIT },
            unavailable = true,
            price = MAXIMUM_PRICE,
        )
        assertOnDetail(maximum, count = 2, price = MAXIMUM_PRICE, status = INACTIVE)
        assertOnDetail(minimum, count = 2, price = MINIMUM_PRICE, status = ACTIVE)
        assertLink(collector, minimum, MINIMUM_PRICE, ACTIVE)
        assertLink(collector, maximum, MAXIMUM_PRICE, INACTIVE)
    }

    @Test
    fun required_price_and_status_block_the_save() {
        val name = collectorName()
        val collector = data.createCollector(name, email())
        val album = albumName("vacio")
        data.createAlbum(album)
        openCollector(name, album)
        detail.tapAddAlbum()
        picker.waitUntilOpen(name).select(album).tapContinue()

        sheet
            .assertOpen(album, name)
            .assertPrice(sheetPrice(0))
            .assertSubmitEnabled()
            .tapAdd()
        sheet.assertPriceError(shown = true).assertStatusError(shown = true).assertSubmitEnabled()
        sheet.enterDigits(MINIMUM_PRICE).assertPrice(sheetPrice(MINIMUM_PRICE))
        sheet.assertPriceError(shown = false).assertStatusError(shown = false)
        sheet.tapAdd().assertPriceError(shown = false).assertStatusError(shown = true)
        sheet.chooseAvailable().assertStatusError(shown = false)
        sheet
            .tapDelete()
            .assertPrice(sheetPrice(0))
            .assertPriceError(shown = false)
            .assertStatusError(shown = false)
        sheet
            .enterDigits(*digitsOf(CLEARED_PRICE))
            .tapClear()
            .assertPrice(sheetPrice(0))
            .tapClose()

        picker.assertContinue(album)
        assertTrue(data.albumsOf(collector).isEmpty())
    }

    @Test
    fun visitor_does_not_see_the_add_action() {
        val name = collectorName()
        val collector = data.createCollector(name, email())

        catalogs.enterAs(R.string.role_visitor)
        collectors
            .openCollectorsTab()
            .waitForList()
            .pullToRefresh()
            .tapRow(name)
        detail.waitUntilReady(name).assertAddActionsHidden()

        assertTrue(data.albumsOf(collector).isEmpty())
    }

    @Test
    fun cancel_and_close_write_nothing_and_two_taps_open_one_sheet() {
        val name = collectorName()
        val collector = data.createCollector(name, email())
        val album = albumName("descartado")
        data.createAlbum(album)
        openCollector(name, album)

        detail.tapAddAlbum()
        picker
            .waitUntilOpen(name)
            .select(album)
            .assertContinue(album)
            .tapClose()
        detail.waitUntilReady(name)
        onView(withText(context.getString(R.string.collector_album_selected, album))).check(doesNotExist())

        detail.tapAddAlbum()
        picker.waitUntilOpen(name).select(album)
        onView(withId(R.id.continueButton)).perform(tapTwiceInTheSameFrame())
        sheet
            .assertOpen(album, name)
            .enterDigits(*digitsOf(SAMPLE_PRICE))
            .chooseAvailable()
            .tapCancel()
        picker.assertContinue(album).tapContinue()
        sheet.assertPrice(sheetPrice(0)).tapClose()
        picker.assertContinue(album).tapClose()
        detail.waitUntilReady(name)

        assertTrue(data.albumsOf(collector).isEmpty())
    }

    @Test
    fun search_blocks_an_album_already_added_and_allows_one_from_another_collector() {
        val firstName = collectorName()
        val secondName = collectorName()
        val first = data.createCollector(firstName, email())
        val second = data.createCollector(secondName, email())
        val owned = albumName("Propio")
        val foreign = albumName("Ajeno")
        data.link(first, data.createAlbum(owned), PREPARED_PRICE, ACTIVE)
        data.link(second, data.createAlbum(foreign, FOLK), PREPARED_PRICE, INACTIVE)
        openCollector(firstName, owned, foreign)

        detail.tapAddAlbum()
        picker
            .waitUntilOpen(firstName)
            .search("sin-resultado-$owned")
            .assertNoResults()
            .assertContinueDisabled()
        picker.search(owned.lowercase()).assertAlreadyAdded(owned).assertRow(owned, genreMeta(ROCK))
        picker.assertContinueDisabled()
        picker
            .search(FOLK.lowercase())
            .assertRow(foreign, genreMeta(FOLK))
            .select(foreign)
            .assertContinue(foreign)
        picker.tapClose()
        detail.waitUntilReady(firstName)

        assertLink(first, owned, PREPARED_PRICE, ACTIVE)
        assertEquals(1, data.albumsOf(first).size)
        assertLink(second, foreign, PREPARED_PRICE, INACTIVE)
    }

    @Test
    fun network_failure_keeps_the_form_and_a_retry_saves_once() {
        val name = collectorName()
        val collector = data.createCollector(name, email())
        val album = albumName("red")
        data.createAlbum(album)
        openCollector(name, album)
        detail.tapAddAlbum()
        picker.waitUntilOpen(name).select(album).tapContinue()
        sheet.assertOpen(album, name).enterDigits(*digitsOf(SAMPLE_PRICE)).chooseAvailable()
        sheet.assertPrice(sheetPrice(SAMPLE_PRICE))

        firewall.block()
        sheet.tapAdd().assertNetworkError().assertPrice(sheetPrice(SAMPLE_PRICE))
        firewall.restore()
        sheet.tapAdd()

        waitForDisplayed(withText(context.getString(R.string.collector_album_selected, album)))
        assertOnDetail(album, count = 1, price = SAMPLE_PRICE, status = ACTIVE)
        assertEquals(1, data.albumsOf(collector).size)
        assertLink(collector, album, SAMPLE_PRICE, ACTIVE)
    }

    @Test
    fun picker_network_error_recovers_and_selection_survives_recreation() {
        val name = collectorName()
        val collector = data.createCollector(name, email())
        val album = albumName("recrear")
        data.createAlbum(album)
        openCollector(name, album)

        firewall.block()
        detail.tapAddAlbum()
        picker.assertNetworkError()
        firewall.restore()
        picker
            .tapRetry()
            .waitUntilOpen(name)
            .search(album)
            .select(album)
            .assertContinue(album)

        activityRule.scenario.recreate()

        picker.assertSearch(album).assertContinue(album).tapClose()
        detail.waitUntilReady(name)
        assertTrue(data.albumsOf(collector).isEmpty())
    }

    private fun add(
        collectorName: String,
        albumName: String,
        digits: IntArray,
        unavailable: Boolean,
        price: Int,
    ) {
        detail.tapAddAlbum()
        picker
            .waitUntilOpen(collectorName)
            .search(albumName)
            .select(albumName)
            .tapContinue()
        sheet.assertOpen(albumName, collectorName).enterDigits(*digits).assertPrice(sheetPrice(price))
        if (unavailable) sheet.chooseUnavailable() else sheet.chooseAvailable()
        sheet.tapAdd()
        val saved = withText(context.getString(R.string.collector_album_selected, albumName))
        waitForDisplayed(saved)
        // En el Nexus 5 el aviso tapa «Agregar álbum» y el toque no abre el selector.
        waitUntilGone(saved)
    }

    private fun openCollector(
        name: String,
        vararg albums: String,
    ) {
        catalogs.enterAs(R.string.role_collector).pullToRefresh()
        albums.forEach { waitForCatalog(it) }
        collectors
            .openCollectorsTab()
            .waitForList()
            .pullToRefresh()
            .tapRow(name)
        detail.waitUntilReady(name)
    }

    private fun waitForCatalog(name: String) {
        val deadline = System.currentTimeMillis() + CATALOG_TIMEOUT_MILLIS
        var last: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.albumList)).perform(
                    RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(name))),
                )
                return
            } catch (error: PerformException) {
                last = error
            } catch (error: AssertionError) {
                last = error
            }
            Thread.sleep(POLL_MILLIS)
        }
        fail("El catálogo no mostró $name ($last)")
    }

    private fun assertOnDetail(
        name: String,
        count: Int,
        price: Int,
        status: String,
    ) {
        val section = context.getString(R.string.collector_section_albums, count)
        waitForDisplayed(allOf(withId(R.id.sectionTitle), withText(section), isDisplayed()))
        detail.assertAlbums(listOf(AlbumSnapshot(name = name, price = price, status = status)))
    }

    private fun assertLink(
        collectorId: Int,
        albumName: String,
        price: Int,
        status: String,
    ) {
        val link = data.albumsOf(collectorId).single { it.albumName == albumName }
        assertEquals(price, link.price)
        assertEquals(status, link.status)
    }

    private fun collectorName() = "Coleccionista ${token()}"

    private fun albumName(suffix: String) = "Album ${token()} $suffix"

    private fun email() = "a${token()}@example.com"

    private fun token() = System.nanoTime().toString()

    private fun genreMeta(genre: String) =
        context.getString(R.string.album_genre_year, genre, BackendCollectorAlbums.RELEASE_YEAR)

    private fun sheetPrice(amount: Int) = "$$amount"

    private fun digitsOf(amount: Int): IntArray = amount.toString().map { it.digitToInt() }.toIntArray()

    private fun tapTwiceInTheSameFrame(): ViewAction =
        object : ViewAction {
            override fun getConstraints(): Matcher<View> = isDisplayed()

            override fun getDescription() = "Dos toques antes de que termine la primera transición"

            override fun perform(
                uiController: UiController,
                view: View,
            ) {
                view.performClick()
                view.performClick()
                uiController.loopMainThreadUntilIdle()
            }
        }

    private companion object {
        const val DIGITS_PAST_THE_MAXIMUM = 8
        const val MAXIMUM_DIGIT = 9
    }
}
