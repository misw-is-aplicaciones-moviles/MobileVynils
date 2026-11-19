package co.edu.uniandes.vinilos.robots

import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.FailureHandler
import androidx.test.espresso.PerformException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.clickListener
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Assert.fail

private const val POLL_MILLIS = 200L
private const val ROW_TIMEOUT_MILLIS = 15_000L

// Los sondeos fallan a propósito mientras llegan las filas; sin capturas para no congelar emuladores lentos.
private val rethrow = FailureHandler { error, _ -> throw error }

/** Page Object de "Elegir músico" y del botón "Agregar integrante" del detalle de banda. */
class BandMemberPickerRobot {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val detail = PerformerDetailRobot()

    /** Desde el detalle de la banda, baja hasta "Agregar integrante", lo toca y espera el selector. */
    fun openFromBandDetail(): BandMemberPickerRobot {
        waitForDisplayed(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withId(R.id.addMemberButton))),
        )
        onView(withId(R.id.addMemberButton)).check(matches(allOf(isDisplayed(), isEnabled()))).perform(click())
        waitForDisplayed(withText(R.string.band_member_picker_title))
        return this
    }

    /** El botón del detalle no existe o no se ve (Visitante o detalle de músico). */
    fun assertAddMemberHidden(): BandMemberPickerRobot {
        onView(allOf(withId(R.id.addMemberButton), isDisplayed())).check(doesNotExist())
        return this
    }

    fun search(text: String): BandMemberPickerRobot {
        onView(withId(R.id.musicianSearch)).perform(replaceText(text), closeSoftKeyboard())
        return this
    }

    /** Espera a que la fila de [name] llegue a la lista (se calcula fuera del hilo principal) y la muestra. */
    fun scrollTo(name: String): BandMemberPickerRobot {
        val deadline = System.currentTimeMillis() + ROW_TIMEOUT_MILLIS
        while (true) {
            try {
                onView(withId(R.id.musicianList))
                    .withFailureHandler(rethrow)
                    .perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(name))))
                return this
            } catch (error: PerformException) {
                if (System.currentTimeMillis() > deadline) fail("La fila \"$name\" no llegó al selector: $error")
                Thread.sleep(POLL_MILLIS)
            }
        }
    }

    fun select(name: String): BandMemberPickerRobot {
        scrollTo(name)
        onView(row(name)).perform(click())
        return this
    }

    fun assertSelected(
        name: String,
        selected: Boolean,
    ): BandMemberPickerRobot {
        onView(row(name)).check(matches(if (selected) isSelected() else not(isSelected())))
        return this
    }

    /** La fila muestra "YA INTEGRANTE". */
    fun assertAlreadyMember(name: String): BandMemberPickerRobot {
        scrollTo(name)
        onView(row(name)).check(matches(hasDescendant(allOf(withId(R.id.alreadyMember), isDisplayed()))))
        return this
    }

    fun assertOverlineContains(
        name: String,
        text: String,
    ): BandMemberPickerRobot {
        scrollTo(name)
        onView(
            row(name),
        ).check(matches(hasDescendant(allOf(withId(R.id.musicianOverline), withText(containsString(text))))))
        return this
    }

    fun assertButton(
        text: String,
        enabled: Boolean,
    ): BandMemberPickerRobot {
        onView(
            withId(R.id.addMember),
        ).check(matches(allOf(withText(text), if (enabled) isEnabled() else not(isEnabled()))))
        return this
    }

    fun assertSelectButtonDisabled() =
        assertButton(context.getString(R.string.band_member_select_action), enabled = false)

    fun tapAdd(): BandMemberPickerRobot {
        onView(withId(R.id.addMember)).perform(click())
        return this
    }

    fun assertConflict(
        musician: String,
        band: String,
    ): BandMemberPickerRobot {
        waitForDisplayed(
            allOf(
                withId(R.id.memberNotice),
                withText(context.getString(R.string.band_member_conflict, musician, band)),
            ),
        )
        onView(withText(R.string.band_member_picker_title)).check(matches(isDisplayed()))
        return this
    }

    /** El aviso del selector muestra [message] y la pantalla sigue abierta. */
    fun assertNotice(
        @StringRes message: Int,
    ): BandMemberPickerRobot {
        waitForDisplayed(allOf(withId(R.id.memberNotice), withText(message)))
        onView(withText(R.string.band_member_picker_title)).check(matches(isDisplayed()))
        return this
    }

    fun assertNoResults(): BandMemberPickerRobot {
        waitForDisplayed(allOf(withId(R.id.emptyMessage), withText(R.string.band_member_no_results)))
        return this
    }

    fun assertLoadError(): BandMemberPickerRobot {
        waitForDisplayed(allOf(withId(R.id.retryButton), isDescendantOfA(withId(R.id.errorState))))
        return this
    }

    fun tapRetry(): BandMemberPickerRobot {
        // En API 21 el botón queda cortado y el clic de Espresso exige el 90 % del área.
        onView(allOf(withId(R.id.retryButton), isDescendantOfA(withId(R.id.errorState)))).perform(clickListener())
        return this
    }

    fun close(): BandMemberPickerRobot {
        onView(withId(R.id.headerClose)).perform(click())
        return this
    }

    /** De vuelta en el detalle, el integrante está en la tira y el contador lo cuenta. */
    fun assertMemberInBand(
        name: String,
        count: Int,
    ): BandMemberPickerRobot {
        detail.scrollToText(context.getString(R.string.artist_detail_members, count), R.id.membersLabel)
        waitForDisplayed(withId(R.id.performerMembers)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(name))),
        )
        waitForDisplayed(allOf(withId(R.id.memberName), withText(name)))
        return this
    }

    // La fila es el padre del bloque que contiene el nombre: name -> columna -> contenido -> fila.
    private fun row(name: String) =
        allOf(
            isDescendantOfA(withId(R.id.musicianList)),
            withParent(withId(R.id.musicianList)),
            hasDescendant(allOf(withId(R.id.musicianName), withText(name))),
        )
}
