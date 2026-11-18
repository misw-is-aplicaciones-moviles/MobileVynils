package co.edu.uniandes.vinilos.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
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
import co.edu.uniandes.vinilos.robots.BandMemberPickerRobot
import co.edu.uniandes.vinilos.robots.PerformerDetailRobot
import co.edu.uniandes.vinilos.robots.PerformerListRobot
import co.edu.uniandes.vinilos.testing.BackendFirewall
import co.edu.uniandes.vinilos.testing.BackendPerformers
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import co.edu.uniandes.vinilos.testing.waitUntilGone
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * Agregar músicos a una banda contra BackVynils local. Cada caso crea solo los artistas que necesita, con nombres
 * únicos, y los borra al terminar. Con las secuencias del backend en 1, una corrida completa no puede crear más de 99
 * artistas sin chocar con los ids 100 y 101 de la semilla, así que aquí no se crea nada que el caso no use.
 */
@RunWith(AndroidJUnit4::class)
class BandMemberPickerScreenTest {
    private val data = BackendPerformers()
    private val firewall = BackendFirewall()
    private val activityRule = ActivityScenarioRule(MainActivity::class.java)

    // La limpieza de los datos se ejecuta al final: el corte de red ya se restauró y la actividad ya se cerró.
    @get:Rule val ruleChain: TestRule = RuleChain.outerRule(data).around(firewall).around(activityRule)

    private val artists = PerformerListRobot()
    private val picker = BandMemberPickerRobot()
    private val detail = PerformerDetailRobot()
    private val unique = System.nanoTime()
    private val band = "E2E banda $unique"
    private val member = "E2E integrante $unique"
    private val free = "E2E libre $unique"
    private val busy = "E2E ocupado $unique"
    private val otherBand = "E2E otra banda $unique"
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun collector_adds_a_free_musician_and_sees_it_in_the_band() {
        val memberId = data.createMusician(member)
        val freeId = data.createMusician(free)
        val bandId = data.createBand(band, members = listOf(memberId))

        openBandAsCollector()
        picker
            .openFromBandDetail()
            .select(free)
            .assertButton(addTo(band), enabled = true)
            .tapAdd()

        waitForDisplayed(withText(R.string.band_member_added_message))
        picker.assertMemberInBand(free, count = 2)
        assertEquals(setOf(memberId, freeId), data.memberIds(bandId))
    }

    @Test
    fun added_member_is_still_there_after_reopening_the_band() {
        val (bandId, freeId) = bandAndFreeMusician()
        openBandAsCollector()
        picker.openFromBandDetail().select(free).tapAdd()
        waitForDisplayed(withText(R.string.band_member_added_message))

        pressBack()
        artists.waitForList().tapRow(band)

        waitForDisplayed(allOf(withId(R.id.performerName), withText(band)))
        picker.assertMemberInBand(free, count = 1)
        assertTrue(freeId in data.memberIds(bandId))
    }

    @Test
    fun current_members_are_labelled_and_cannot_be_selected() {
        data.createBand(band, members = listOf(data.createMusician(member)))
        openBandAsCollector()

        picker.openFromBandDetail().assertAlreadyMember(member)
        picker.select(member).assertSelected(member, selected = false).assertSelectButtonDisabled()
    }

    @Test
    fun musician_of_another_band_shows_the_notice_and_is_not_added() {
        val busyId = data.createMusician(busy)
        val bandId = data.createBand(band)
        val otherBandId = data.createBand(otherBand, members = listOf(busyId))
        openBandAsCollector()

        picker
            .openFromBandDetail()
            .assertOverlineContains(busy, context.getString(R.string.artist_member_of, otherBand))
            .select(busy)
            .tapAdd()
            .assertConflict(busy, otherBand)

        assertEquals(emptySet<Int>(), data.memberIds(bandId))
        assertEquals(setOf(busyId), data.memberIds(otherBandId))
    }

    @Test
    fun search_filters_musicians_and_shows_no_results() {
        bandAndFreeMusician()
        openBandAsCollector()

        picker.openFromBandDetail().search(free).scrollTo(free)
        // Rubén Blades siempre está en la semilla: la búsqueda debe dejarlo fuera. El filtro corre fuera del hilo
        // principal, así que se espera a que su fila desaparezca en vez de comprobarlo una sola vez.
        waitUntilGone(allOf(withId(R.id.musicianName), withText(SEED_MUSICIAN)))
        picker.search("E2E sin coincidencias $unique").assertNoResults()
    }

    @Test
    fun closing_the_picker_adds_nothing() {
        val (bandId) = bandAndFreeMusician()
        openBandAsCollector()

        picker.openFromBandDetail().select(free).close()
        waitForDisplayed(allOf(withId(R.id.performerName), withText(band)))
        picker.openFromBandDetail().select(free)
        pressBack()

        waitForDisplayed(allOf(withId(R.id.performerName), withText(band)))
        onView(withText(R.string.band_member_added_message)).check(doesNotExist())
        assertEquals(emptySet<Int>(), data.memberIds(bandId))
    }

    @Test
    fun visitor_does_not_see_add_member() {
        data.createBand(band)
        artists.enterAs(R.string.role_visitor).tapRow(band)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(band)))

        detail.scrollToText(context.getString(R.string.artist_detail_members, 0), R.id.membersLabel)
        picker.assertAddMemberHidden()
    }

    @Test
    fun musician_detail_has_no_add_member() {
        data.createMusician(free)
        artists.enterAs(R.string.role_collector).tapRow(free)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(free)))

        onView(withId(R.id.membersLabel)).check(doesNotExist())
        picker.assertAddMemberHidden()
    }

    @Test
    fun empty_band_gets_its_first_member() {
        val (bandId, freeId) = bandAndFreeMusician()
        openBandAsCollector()
        detail.scrollToText(context.getString(R.string.artist_detail_no_members))

        picker.openFromBandDetail().select(free).tapAdd()

        waitForDisplayed(withText(R.string.band_member_added_message))
        picker.assertMemberInBand(free, count = 1)
        onView(withText(R.string.artist_detail_no_members)).check(doesNotExist())
        assertEquals(setOf(freeId), data.memberIds(bandId))
    }

    @Test
    fun recreating_keeps_search_and_selection() {
        bandAndFreeMusician()
        openBandAsCollector()
        picker.openFromBandDetail().search(free).select(free)

        activityRule.scenario.recreate()

        waitForDisplayed(allOf(withId(R.id.musicianSearch), withText(free)))
        picker.scrollTo(free).assertSelected(free, selected = true).assertButton(addTo(band), enabled = true)
    }

    @Test
    fun role_chip_opens_the_role_selector() {
        data.createBand(band)
        openBandAsCollector()
        picker.openFromBandDetail()

        onView(withId(R.id.roleChip)).perform(click())

        waitForDisplayed(withId(R.id.roleSelect))
        waitUntilGone(withText(R.string.band_member_picker_title))
    }

    @Test
    fun load_failure_offers_retry() {
        bandAndFreeMusician()
        openBandAsCollector()

        firewall.block()
        picker.openFromBandDetail().assertLoadError()
        firewall.restore()
        picker.tapRetry().scrollTo(free)

        onView(withId(R.id.errorState)).check(matches(not(isDisplayed())))
    }

    @Test
    fun network_failure_while_adding_asks_to_review_the_band() {
        val (bandId) = bandAndFreeMusician()
        openBandAsCollector()
        picker.openFromBandDetail().select(free)

        firewall.block()
        picker.tapAdd().assertNotice(R.string.band_member_uncertain)
        picker.assertButton(context.getString(R.string.band_member_review_action), enabled = true)
        firewall.restore()
        picker.tapAdd()

        waitForDisplayed(allOf(withId(R.id.performerName), withText(band)))
        detail.scrollToText(context.getString(R.string.artist_detail_members, 0), R.id.membersLabel)
        onView(withText(R.string.band_member_added_message)).check(doesNotExist())
        assertEquals(emptySet<Int>(), data.memberIds(bandId))
    }

    @Test
    fun musician_deleted_while_choosing_shows_not_found() {
        val (bandId, freeId) = bandAndFreeMusician()
        openBandAsCollector()
        picker.openFromBandDetail().select(free)

        data.deleteMusician(freeId)
        picker.tapAdd()

        picker.assertNotice(R.string.band_member_not_found).assertButton(addTo(band), enabled = true)
        assertEquals(emptySet<Int>(), data.memberIds(bandId))
    }

    private fun openBandAsCollector() {
        artists.enterAs(R.string.role_collector).tapRow(band)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(band)))
    }

    private fun addTo(name: String) = context.getString(R.string.band_member_add_action, name)

    /** Una banda sin integrantes y un músico libre: lo mínimo para agregar a alguien. Devuelve sus ids. */
    private fun bandAndFreeMusician(): Pair<Int, Int> {
        val freeId = data.createMusician(free)
        return data.createBand(band) to freeId
    }

    private companion object {
        const val SEED_MUSICIAN = "Rubén Blades Bellido de Luna"
    }
}
