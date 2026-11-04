package co.edu.uniandes.vinilos.ui

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withAlpha
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.robots.PerformerDetailRobot
import co.edu.uniandes.vinilos.robots.PerformerListRobot
import co.edu.uniandes.vinilos.testing.BackendFirewall
import co.edu.uniandes.vinilos.testing.BackendPrizes
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import co.edu.uniandes.vinilos.testing.waitUntilGone
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** HU14 desde la entrada real de HU04; run_e2e.sh limpia solo los fixtures de esta ejecución. */
@RunWith(AndroidJUnit4::class)
class PrizePickerScreenTest {
    private val firewall = BackendFirewall()
    private val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule val rules: TestRule = RuleChain.outerRule(firewall).around(activityRule)
    private val artists = PerformerListRobot()
    private val detail = PerformerDetailRobot()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var previousNightMode = AppCompatDelegate.MODE_NIGHT_UNSPECIFIED

    @Before
    fun configureTheme() {
        previousNightMode = AppCompatDelegate.getDefaultNightMode()
        val dark = InstrumentationRegistry.getArguments().getString("darkMode") ?: return
        activityRule.scenario.onActivity {
            AppCompatDelegate.setDefaultNightMode(
                if (dark == "true") AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO,
            )
        }
    }

    @After
    fun restoreTheme() {
        activityRule.scenario.onActivity { AppCompatDelegate.setDefaultNightMode(previousNightMode) }
    }

    @Test
    fun assigns_prize_to_musician_and_updates_its_detail() = assign(PerformerType.MUSICIAN)

    @Test
    fun assigns_prize_to_band_and_updates_its_detail() = assign(PerformerType.BAND)

    @Test
    fun search_blocks_own_assignments_but_allows_a_prize_assigned_to_another_artist() {
        val fixture = fixture(PerformerType.MUSICIAN)
        fixture.assign()
        val other = fixture(PerformerType.BAND)
        other.assign()
        open(fixture, assigned = 1)
        filter(fixture.name)
        waitForDisplayed(withText(R.string.prize_already_assigned))
        select(fixture.name)
        onView(withId(R.id.continuePrize)).check(matches(not(isEnabled())))
        filter("Sin coincidencias ${fixture.name}")
        waitForDisplayed(withText(R.string.prize_no_results))
        filter(other.name)
        select(other.name)
        onView(withId(R.id.continuePrize)).check(matches(isEnabled()))
        capture("hu14-selector.png")
        onView(withId(R.id.headerClose)).perform(click())
        detail.scrollToText(context.getString(R.string.prize_detail_count, 1))
        assertEquals(1, fixture.artist().getJSONArray("performerPrizes").length())
    }

    @Test
    fun required_date_and_selection_survive_recreation_and_cancel_does_not_write() {
        val fixture = fixture(PerformerType.MUSICIAN)
        open(fixture)
        filter(fixture.name)
        select(fixture.name)
        onView(withId(R.id.continuePrize)).perform(click())
        waitForDisplayed(withId(R.id.savePrize)).check(matches(not(isEnabled())))
        capture("hu14-fecha-obligatoria.png")
        val selectedDate = chooseDate()
        activityRule.scenario.recreate()
        waitForDisplayed(withId(R.id.savePrize)).check(matches(isEnabled()))
        onView(withId(R.id.prizeDate)).check(
            matches(
                withText(
                    selectedDate.format(DateTimeFormatter.ofPattern("dd / MM / yyyy", Locale.getDefault())),
                ),
            ),
        )
        onView(withId(R.id.sheetContext)).check(
            matches(
                withText(
                    context.getString(R.string.prize_date_context, fixture.name, fixture.name),
                ),
            ),
        )
        capture("hu14-fecha-restaurada.png")
        onView(withId(R.id.closeSheet)).perform(click())
        waitUntilGone(withId(R.id.savePrize))
        onView(withId(R.id.continuePrize)).check(matches(isEnabled())).perform(click())
        waitForDisplayed(withId(R.id.savePrize)).check(matches(isEnabled()))
        onView(withId(R.id.closeSheet)).perform(click())
        waitUntilGone(withId(R.id.savePrize))
        onView(withId(R.id.headerClose)).perform(click())
        detail.scrollToText(context.getString(R.string.prize_detail_count, 0))
        assertEquals(0, fixture.artist().getJSONArray("performerPrizes").length())
    }

    @Test
    fun network_failure_requires_review_before_a_new_assignment_and_then_recovers() {
        val fixture = fixture(PerformerType.MUSICIAN)
        open(fixture)
        filter(fixture.name)
        select(fixture.name)
        onView(withId(R.id.continuePrize)).perform(click())
        waitForDisplayed(withId(R.id.savePrize)).check(matches(not(isEnabled())))
        chooseDate()
        firewall.block()
        onView(withId(R.id.savePrize)).perform(click())
        waitForDisplayed(withText(R.string.prize_assign_uncertain))
        onView(withId(R.id.savePrize)).check(matches(not(isEnabled())))
        capture("hu14-resultado-incierto.png")
        firewall.restore()
        assertEquals(0, fixture.artist().getJSONArray("performerPrizes").length())
        onView(withId(R.id.reviewArtist)).perform(click())
        waitUntilGone(withId(R.id.prizeSearch))
        detail.scrollToText(context.getString(R.string.prize_detail_count, 0))
        onView(withId(R.id.addPrizeButton)).check(matches(isEnabled())).perform(click())
        waitForDisplayed(withId(R.id.prizeSearch))
        filter(fixture.name)
        select(fixture.name)
        onView(withId(R.id.continuePrize)).perform(click())
        waitForDisplayed(withId(R.id.savePrize))
        val date = chooseDate()
        onView(withId(R.id.savePrize)).perform(click())
        assertSaved(fixture, date)
    }

    private fun assign(type: PerformerType) {
        val fixture = fixture(type)
        open(fixture)
        filter(fixture.name)
        select(fixture.name)
        onView(withId(R.id.continuePrize)).perform(click())
        waitForDisplayed(withId(R.id.savePrize)).check(matches(not(isEnabled())))
        val date = chooseDate()
        onView(withId(R.id.savePrize)).perform(click())
        assertSaved(fixture, date)
        capture("hu14-aviso-${type.name.lowercase()}.png")
        // El resultado se consume una vez: recrear no duplica la fila ni el POST.
        activityRule.scenario.recreate()
        detail.scrollToText(context.getString(R.string.prize_detail_count, 1))
        detail.scrollToText(fixture.name, R.id.prizeName)
        onView(allOf(withId(R.id.prizeName), withText(fixture.name))).check(matches(isDisplayed()))
        assertEquals(1, fixture.artist().getJSONArray("performerPrizes").length())
        capture("hu14-detalle-${type.name.lowercase()}.png")
    }

    private fun assertSaved(
        fixture: BackendPrizes,
        date: LocalDate,
    ) {
        waitUntilGone(withId(R.id.prizeSearch))
        waitForDisplayed(withText(R.string.prize_assigned_message))
        detail.scrollToText(context.getString(R.string.prize_detail_count, 1))
        detail.scrollToText(fixture.name, R.id.prizeName)
        // RecyclerView puede mantener la fila insertada transparente durante su animación.
        waitForDisplayed(
            allOf(
                withId(R.id.prizeName),
                withText(fixture.name),
                isDescendantOfA(allOf(isSelected(), withAlpha(1f))),
            ),
        )
        onView(
            allOf(withId(R.id.prizeName), withText(fixture.name)),
        ).check(matches(allOf(isDisplayed(), isDescendantOfA(isSelected()))))
        onView(withId(R.id.prizeOrganization)).check(matches(allOf(isDisplayed(), withText("Equipo de pruebas HU14"))))
        val shownDate = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
        onView(withId(R.id.alreadyAssigned)).check(matches(allOf(isDisplayed(), withText(shownDate))))
        val saved = fixture.artist().getJSONArray("performerPrizes")
        assertEquals(1, saved.length())
        assertEquals("${date}T00:00:00.000Z", saved.getJSONObject(0).getString("premiationDate"))
    }

    private fun fixture(type: PerformerType) = BackendPrizes(type).apply { prepare() }

    private fun open(
        fixture: BackendPrizes,
        assigned: Int = 0,
    ) {
        artists.enterAs(R.string.role_collector).tapRow(fixture.name)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(fixture.name)))
        detail.scrollToText(context.getString(R.string.prize_detail_count, assigned))
        onView(withId(R.id.addPrizeButton)).check(matches(isEnabled())).perform(click())
        waitForDisplayed(withId(R.id.prizeSearch))
        onView(withId(R.id.bottomNav)).check(matches(not(isDisplayed())))
    }

    private fun filter(query: String) {
        onView(withId(R.id.prizeSearch)).perform(replaceText(query))
        closeSoftKeyboard()
    }

    private fun select(name: String) {
        waitForDisplayed(allOf(withId(R.id.prizeName), withText(name)))
        onView(withId(R.id.prizeList)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(hasDescendant(withText(name)), click()),
        )
    }

    private fun chooseDate(): LocalDate {
        onView(withId(R.id.prizeDate)).perform(click())
        val date = LocalDate.now(ZoneOffset.UTC)
        onData(equalTo(TimeUnit.DAYS.toMillis(date.toEpochDay())))
            .inAdapterView(allOf(withId(com.google.android.material.R.id.month_grid), isDisplayed()))
            .perform(click())
        onView(withId(com.google.android.material.R.id.confirm_button)).perform(click())
        return date
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureScreenshots") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(SCREENSHOT_IDLE_MILLIS, SCREENSHOT_TIMEOUT_MILLIS)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        bitmap.recycle()
    }

    private companion object {
        const val PNG_QUALITY = 100
        const val SCREENSHOT_IDLE_MILLIS = 100L
        const val SCREENSHOT_TIMEOUT_MILLIS = 3_000L
    }
}
