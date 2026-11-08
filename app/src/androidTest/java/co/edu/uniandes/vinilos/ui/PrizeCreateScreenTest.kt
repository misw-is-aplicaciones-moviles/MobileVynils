package co.edu.uniandes.vinilos.ui

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.FailureHandler
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isRoot
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
import co.edu.uniandes.vinilos.robots.PrizeCreateRobot
import co.edu.uniandes.vinilos.testing.BackendFirewall
import co.edu.uniandes.vinilos.testing.BackendPrizes
import co.edu.uniandes.vinilos.testing.clickVisible
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import co.edu.uniandes.vinilos.testing.waitUntilGone
import co.edu.uniandes.vinilos.ui.artists.PrizeAdapter
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/**
 * HU13 por la vía real: Artistas, detalle, "Agregar premio", "Elegir premio" y "Crear un premio nuevo".
 *
 * Los premios que crea la pantalla llevan la marca de la ejecución, la descripción y la organización de los fixtures
 * de HU14. [BackendPrizes] los elimina junto con el artista al terminar.
 */
@RunWith(AndroidJUnit4::class)
class PrizeCreateScreenTest {
    private val prizes = BackendPrizes()
    private val firewall = BackendFirewall()
    private val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule val rules: TestRule = RuleChain.outerRule(prizes).around(firewall).around(activityRule)
    private val artists = PerformerListRobot()
    private val detail = PerformerDetailRobot()
    private val form = PrizeCreateRobot()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun creates_a_prize_and_returns_to_the_picker_with_it_selected() {
        val fixture = fixture()
        val name = fixture.uiPrizeName("nuevo")
        val before = fixture.prizes().size
        openPicker(fixture)
        openCreateForm()

        // Los espacios sobrantes no viajan al backend.
        form
            .typeName("  $name ")
            .typeDescription("\n$PRIZE_DESCRIPTION  ")
            .typeOrganization(" $PRIZE_ORGANIZATION ")
            .tapCreate()

        waitForDisplayed(withId(R.id.prizeSearch))
        waitForDisplayed(createdNotice())
        assertNoticeDoesNotCoverTheButton()
        assertCreatedRowIsFirstAndSelected(name)
        onView(withId(R.id.continuePrize)).check(
            matches(allOf(isEnabled(), withText(context.getString(R.string.prize_continue_action, name)))),
        )
        onView(withId(R.id.createPrize)).check(matches(isDisplayed()))
        val created = fixture.prizes().filter { it.getString("name") == name }
        assertEquals(1, created.size)
        assertEquals(before + 1, fixture.prizes().size)
        assertEquals(PRIZE_DESCRIPTION, created.single().getString("description"))
        assertEquals(PRIZE_ORGANIZATION, created.single().getString("organization"))
        // El aviso se entrega una vez: al recrear la pantalla no vuelve.
        activityRule.scenario.recreate()
        waitForDisplayed(withId(R.id.prizeSearch))
        onView(createdNotice()).check(doesNotExist())
        assertCreatedRowIsFirstAndSelected(name)
    }

    @Test
    fun required_errors_appear_per_field_without_sending_anything() {
        val fixture = fixture()
        val before = fixture.prizes().size
        openPicker(fixture)
        openCreateForm()

        form
            .tapCreate()
            .assertFieldError(R.id.prizeNameError, R.string.error_required)
            .assertFieldError(R.id.prizeDescriptionError, R.string.error_required)
            .assertFieldError(R.id.prizeOrganizationError, R.string.error_required)
            .assertFormShown()
        // Un campo con solo espacios es vacío: el backend lo aceptaría, la pantalla no.
        form
            .typeName("   ")
            .typeDescription(PRIZE_DESCRIPTION)
            .typeOrganization(PRIZE_ORGANIZATION)
            .assertNoFieldError(R.id.prizeDescriptionError)
            .assertNoFieldError(R.id.prizeOrganizationError)
            .tapCreate()
            .assertFieldError(R.id.prizeNameError, R.string.error_required)
            .assertNoFieldError(R.id.prizeDescriptionError)
            .assertNoFieldError(R.id.prizeOrganizationError)
        // Lo escrito y el error sobreviven a recrear la Activity; escribir un nombre limpia solo su error.
        activityRule.scenario.recreate()
        form
            .assertFormShown()
            .assertFieldError(R.id.prizeNameError, R.string.error_required)
            .assertText(R.id.prizeDescriptionInput, PRIZE_DESCRIPTION)
            .assertText(R.id.prizeOrganizationInput, PRIZE_ORGANIZATION)
            .typeName(fixture.uiPrizeName("sin enviar"))
            .assertNoFieldError(R.id.prizeNameError)
        assertEquals(before, fixture.prizes().size)
    }

    @Test
    fun cancel_and_close_return_without_writing_and_keep_the_previous_selection() {
        val fixture = fixture()
        val before = fixture.prizes().size
        openPicker(fixture)
        filter(fixture.name)
        select(fixture.name)
        val selectedLabel = context.getString(R.string.prize_continue_action, fixture.name)
        onView(withId(R.id.continuePrize)).check(matches(withText(selectedLabel)))
        val discarded = fixture.uiPrizeName("descartado")

        openCreateForm()
        form.typeName(discarded).typeDescription(PRIZE_DESCRIPTION).tapCancel()
        waitForDisplayed(withId(R.id.prizeSearch))
        assertPickerKeptTheSelection(fixture.name, selectedLabel)

        // La segunda vez empieza vacía y se cierra con la X.
        openCreateForm()
        form
            .assertText(R.id.prizeNameInput, "")
            .typeName(discarded)
            .typeOrganization(PRIZE_ORGANIZATION)
            .tapClose()
        waitForDisplayed(withId(R.id.prizeSearch))
        assertPickerKeptTheSelection(fixture.name, selectedLabel)
        assertEquals(before, fixture.prizes().size)
        assertTrue(fixture.prizes().none { it.getString("name") == discarded })
    }

    @Test
    fun a_created_prize_continues_to_the_assignment_for_the_artist() {
        val fixture = fixture()
        val name = fixture.uiPrizeName("para asignar")
        openPicker(fixture)
        openCreateForm()
        form
            .typeName(name)
            .typeDescription(PRIZE_DESCRIPTION)
            .typeOrganization(PRIZE_ORGANIZATION)
            .tapCreate()
        waitForDisplayed(createdNotice())
        assertCreatedRowIsFirstAndSelected(name)
        // El aviso de "Premio creado" es corto. Si sigue en pantalla, el de la asignación no llega a verse.
        waitUntilGone(
            allOf(
                withId(com.google.android.material.R.id.snackbar_text),
                withText(R.string.prize_created_message),
            ),
        )

        onView(withId(R.id.continuePrize)).perform(click())
        waitForDisplayed(withId(R.id.savePrize)).check(matches(not(isEnabled())))
        val date = chooseDate()
        onView(withId(R.id.savePrize)).perform(click())

        waitUntilGone(withId(R.id.prizeSearch))
        waitForDisplayed(allOf(withText(R.string.prize_assigned_message), isDisplayed()))
        detail.scrollToText(context.getString(R.string.prize_detail_count, 1))
        detail.scrollToText(name, R.id.prizeName)
        onView(allOf(withId(R.id.prizeName), withText(name))).check(matches(isDisplayed()))
        val saved = fixture.artist().getJSONArray("performerPrizes")
        assertEquals(1, saved.length())
        assertEquals("${date}T00:00:00.000Z", saved.getJSONObject(0).getString("premiationDate"))
        val prize = fixture.prizes().single { it.getString("name") == name }
        assertEquals(
            saved.getJSONObject(0).getInt("id"),
            prize.getJSONArray("performerPrizes").getJSONObject(0).getInt("id"),
        )
    }

    @Test
    fun two_taps_on_the_create_card_open_a_single_form() {
        val fixture = fixture()
        openPicker(fixture)

        onView(withId(R.id.createPrize)).perform(tapTwiceInTheSameFrame())
        form.assertFormShown()
        form.tapClose()

        // Con dos formularios apilados, la X dejaría el segundo y no el selector.
        waitForDisplayed(withId(R.id.prizeSearch))
        onView(withId(R.id.prizeNameInput)).check(doesNotExist())
    }

    @Test
    fun an_unknown_result_locks_the_form_until_the_catalog_is_checked() {
        val fixture = fixture()
        val name = fixture.uiPrizeName("incierto")
        openPicker(fixture)
        openCreateForm()
        form.typeName(name).typeDescription(PRIZE_DESCRIPTION).typeOrganization(PRIZE_ORGANIZATION)

        firewall.block()
        form
            .tapCreate()
            .waitForNotice(
                R.string.create_prize_uncertain,
            ).waitForPrimaryAction(R.string.action_check_prize)
        form.assertFieldsLocked(true)
        // Sin conexión tampoco se puede comprobar, y nada se envía de nuevo.
        form.tapCreate().waitForNotice(R.string.create_prize_check_failed).assertFieldsLocked(true)
        firewall.restore()
        assertTrue(fixture.prizes().none { it.getString("name") == name })

        form.waitForPrimaryAction(R.string.action_check_prize).tapCreate()
        form.waitForNotice(R.string.create_prize_not_saved).waitForPrimaryAction(R.string.action_create_prize)
        form.assertFieldsLocked(false)
        form.tapCreate()

        waitForDisplayed(createdNotice())
        assertCreatedRowIsFirstAndSelected(name)
        assertEquals(1, fixture.prizes().count { it.getString("name") == name })
    }

    private fun fixture() = prizes.open(PerformerType.MUSICIAN)

    private fun openPicker(fixture: BackendPrizes.Fixture) {
        openArtistsAsCollector()
        artists.tapRow(fixture.name)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(fixture.name)))
        detail.scrollToText(context.getString(R.string.prize_detail_count, 0))
        onView(allOf(withId(R.id.addPrizeButton), isEnabled(), isDisplayed())).perform(clickVisible())
        waitForDisplayed(withId(R.id.prizeSearch))
        onView(withId(R.id.bottomNav)).check(matches(not(isDisplayed())))
    }

    /**
     * El toque en la pestaña a veces llega como pulsación larga ("Overslept and turned a tap into a long press" en
     * logcat, conocido en PerformerListScreenTest) y no navega: se repite el toque, no la espera.
     */
    private fun openArtistsAsCollector() {
        artists.chooseRole(R.string.role_collector)
        repeat(TAB_ATTEMPTS - 1) {
            artists.openArtistsTab()
            try {
                waitForDisplayed(withId(R.id.performerList), TAB_WAIT_MILLIS)
                return
            } catch (_: AssertionError) {
                // Otro intento con un toque nuevo.
            }
        }
        artists.openArtistsTab().waitForList()
    }

    private fun openCreateForm() {
        waitForDisplayed(withId(R.id.createPrize)).perform(click())
        form.assertFormShown()
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

    private fun assertPickerKeptTheSelection(
        name: String,
        label: String,
    ) {
        onView(withId(R.id.continuePrize)).check(matches(allOf(isEnabled(), withText(label))))
        waitForDisplayed(
            allOf(withId(R.id.prizeName), withText(name), isDescendantOfA(allOf(isSelected(), withAlpha(1f)))),
        )
        onView(createdNotice()).check(doesNotExist())
    }

    /** Arriba, seleccionado y visible, aunque la búsqueda anterior no lo contuviera. */
    private fun assertCreatedRowIsFirstAndSelected(name: String) {
        onView(withId(R.id.prizeList)).check(matches(hasFirstRow(name)))
        waitForDisplayed(allOf(withId(R.id.prizeName), withText(name), isDescendantOfA(isSelected())))
    }

    // withText también encuentra vistas ocultas o ya descartadas: sin isDisplayed() la espera podría ser ambigua.
    private fun createdNotice(): Matcher<View> =
        allOf(
            withId(com.google.android.material.R.id.snackbar_text),
            withText(R.string.prize_created_message),
            isDisplayed(),
        )

    /** El aviso queda sobre el botón inferior, no encima de él. Con muchas filas puede cubrir un momento la tarjeta. */
    private fun assertNoticeDoesNotCoverTheButton() {
        val deadline = System.currentTimeMillis() + NOTICE_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            var verdict: String? = null
            onView(isRoot()).withFailureHandler(FailureHandler { error, _ -> throw error }).perform(
                measure { root -> verdict = overlapOf(root) },
            )
            when (val result = verdict) {
                null -> Thread.sleep(POLL_MILLIS)
                "" -> return
                else -> fail(result)
            }
        }
        fail("El aviso \"Premio creado\" no se vio para medir su posición")
    }

    /** Vacío si no se cruzan, un mensaje si se cruzan y nulo si el aviso aún no está en pantalla. */
    private fun overlapOf(root: View): String? {
        val text = root.findViewById<View>(com.google.android.material.R.id.snackbar_text)
        if (text == null || !text.isShown) return null
        val snackbar =
            generateSequence(text) { it.parent as? View }.lastOrNull { it.javaClass.name.contains("Snackbar") } ?: text
        val notice = Rect().also { snackbar.getGlobalVisibleRect(it) }
        val button = root.findViewById<View>(R.id.continuePrize)
        val bounds = Rect()
        return when {
            !button.isShown || !button.getGlobalVisibleRect(bounds) -> "El botón no está visible"
            Rect.intersects(notice, bounds) -> "El aviso $notice cubre el botón $bounds"
            else -> ""
        }
    }

    private fun measure(block: (View) -> Unit) =
        object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()

            override fun getDescription() = "Medir las posiciones del aviso, la tarjeta y el botón"

            override fun perform(
                uiController: UiController,
                view: View,
            ) = block(view)
        }

    private fun tapTwiceInTheSameFrame() =
        object : ViewAction {
            override fun getConstraints(): Matcher<View> = isDisplayed()

            override fun getDescription() = "Dos toques antes de que termine la primera navegación"

            override fun perform(
                uiController: UiController,
                view: View,
            ) {
                view.performClick()
                view.performClick()
                uiController.loopMainThreadUntilIdle()
            }
        }

    private fun hasFirstRow(name: String): Matcher<View> =
        object : BoundedMatcher<View, RecyclerView>(RecyclerView::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("lista con \"$name\" como primera fila, seleccionada")
            }

            override fun matchesSafely(list: RecyclerView): Boolean {
                val first = (list.adapter as? PrizeAdapter)?.currentList?.firstOrNull() ?: return false
                return first.prize.name == name && first.selected
            }
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

    private companion object {
        // Con estos dos valores la limpieza de BackendPrizes reconoce el premio que escribió la pantalla.
        const val PRIZE_DESCRIPTION = "Dato HU14"
        const val PRIZE_ORGANIZATION = "Equipo de pruebas HU14"
        const val NOTICE_TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 50L
        const val TAB_ATTEMPTS = 3
        const val TAB_WAIT_MILLIS = 4_000L
    }
}
