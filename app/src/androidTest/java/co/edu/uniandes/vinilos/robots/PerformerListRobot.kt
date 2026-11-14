package co.edu.uniandes.vinilos.robots

import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.FailureHandler
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.testing.clickVisible
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import co.edu.uniandes.vinilos.ui.artists.PerformerAdapter
import co.edu.uniandes.vinilos.viewmodels.PerformerItem
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.fail

private const val POLL_MILLIS = 200L
private const val ROW_TIMEOUT_MILLIS = 15_000L

// Los sondeos de las filas fallan a propósito varias veces: con el manejador por defecto cada fallo guarda una captura,
// y en API 21 esas capturas repetidas congelan el emulador.
private val rethrow = FailureHandler { error, _ -> throw error }

/** Page Object de la lista de artistas: abrir la pestaña, filtrar, leer filas, refrescar y los estados. */
class PerformerListRobot {
    /** Elige el rol en el selector y espera el catálogo, que es donde se abre la app. */
    fun chooseRole(
        @StringRes roleRes: Int,
    ): PerformerListRobot {
        onView(withText(roleRes)).perform(click())
        waitForDisplayed(withId(R.id.albumList))
        return this
    }

    /** Toca la pestaña Artistas de la barra inferior sin esperar la lista, para los casos con error. */
    fun openArtistsTab(): PerformerListRobot {
        onView(allOf(withId(R.id.performerListFragment), isDescendantOfA(withId(R.id.bottomNav)))).perform(click())
        return this
    }

    fun waitForList(): PerformerListRobot {
        waitForDisplayed(withId(R.id.performerList))
        return this
    }

    /** Elige el rol, abre la pestaña Artistas y espera la lista. */
    fun enterAs(
        @StringRes roleRes: Int,
    ): PerformerListRobot = chooseRole(roleRes).openArtistsTab().waitForList()

    fun select(
        @IdRes chipId: Int,
    ): PerformerListRobot {
        onView(withId(chipId)).perform(click())
        return this
    }

    fun assertSelected(
        @IdRes chipId: Int,
    ): PerformerListRobot {
        onView(withId(chipId)).check(matches(isChecked()))
        listOf(R.id.filterAll, R.id.filterMusicians, R.id.filterBands)
            .filter { it != chipId }
            .forEach { onView(withId(it)).check(matches(not(isChecked()))) }
        return this
    }

    /** Busca la fila por nombre y comprueba sobrelínea y tercera línea. */
    fun assertRow(
        name: String,
        overline: String,
        summary: String,
    ): PerformerListRobot {
        scrollTo(name)
        onView(withId(R.id.performerList)).check(matches(hasRow(name, overline, summary)))
        return this
    }

    /** Solo comprueba la sobrelínea: para filas de la semilla cuyas cuentas dependen de otros datos. */
    fun assertOverline(
        name: String,
        overline: String,
    ): PerformerListRobot {
        scrollTo(name)
        onView(withId(R.id.performerList)).check(matches(hasRow(name, overline, null)))
        return this
    }

    /** Espera a que la lista tenga a [name] (llega de Room o de la red) y la desplaza hasta verlo. */
    fun scrollTo(name: String): PerformerListRobot {
        val deadline = System.currentTimeMillis() + ROW_TIMEOUT_MILLIS
        while (true) {
            try {
                onView(withId(R.id.performerList))
                    .withFailureHandler(rethrow)
                    .perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(name))))
                return this
            } catch (error: PerformException) {
                if (System.currentTimeMillis() > deadline) fail("La fila \"$name\" no llegó a la lista: $error")
                Thread.sleep(POLL_MILLIS)
            }
        }
    }

    /** Comprueba, con la lista ya cargada, que [name] está o no está entre las filas del adaptador. */
    fun assertListed(
        name: String,
        listed: Boolean,
    ): PerformerListRobot {
        onView(withId(R.id.performerList))
            .withFailureHandler(rethrow)
            .check(matches(hasItems { items -> items.any { it.name == name } == listed }))
        return this
    }

    fun assertOnlyType(type: PerformerType): PerformerListRobot {
        onView(withId(R.id.performerList))
            .check(matches(hasItems { items -> items.isNotEmpty() && items.all { it.type == type } }))
        return this
    }

    fun assertBothTypes(): PerformerListRobot {
        onView(withId(R.id.performerList))
            .check(matches(hasItems { items -> items.map { it.type }.toSet().size == PerformerType.entries.size }))
        return this
    }

    /** El orden de toda la lista, aunque haya artistas ajenos a la prueba: por nombre según SQLite `COLLATE NOCASE`. */
    fun assertAlphabeticalOrder(): PerformerListRobot {
        onView(withId(R.id.performerList)).check(
            matches(
                hasItems { items ->
                    val names = items.map { it.name }
                    // NOCASE ignora mayúsculas ASCII; conserva las letras acentuadas.
                    names.isNotEmpty() && names == names.sortedBy { folded(it) }
                },
            ),
        )
        return this
    }

    fun assertCreateButtonHidden(): PerformerListRobot {
        onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())))
        return this
    }

    fun pullToRefresh(): PerformerListRobot {
        onView(withId(R.id.performerList))
            .perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(0))
        onView(withId(R.id.swipeRefresh)).perform(swipeDown())
        return this
    }

    fun tapRow(name: String): PerformerListRobot {
        scrollTo(name)
        onView(withId(R.id.performerList)).perform(tapWhenVisible(name))
        return this
    }

    /**
     * Tras filtrar, en API 21 la fila ya está en el adaptador pero `getGlobalVisibleRect` sigue vacío
     * mientras la lista anima el recorte y aplica el scroll. El toque espera a que esa área exista.
     */
    private fun tapWhenVisible(name: String): ViewAction =
        object : ViewAction {
            override fun getConstraints(): Matcher<View> =
                allOf(isAssignableFrom(RecyclerView::class.java), isDisplayed())

            override fun getDescription() = "tap row \"$name\" once its visible area exists"

            override fun perform(
                uiController: UiController,
                view: View,
            ) {
                val row = rowWithVisibleArea(uiController, view as RecyclerView, name)
                clickVisible().perform(uiController, row)
            }
        }

    private fun rowWithVisibleArea(
        uiController: UiController,
        list: RecyclerView,
        name: String,
    ): View {
        val position = positionOf(list, name)
        val manager = list.layoutManager as? LinearLayoutManager
        checkNotNull(manager) { "La lista de artistas no es lineal" }
        val deadline = SystemClock.uptimeMillis() + ROW_TIMEOUT_MILLIS
        val visible = Rect()
        var row: View? = null
        while (row == null && SystemClock.uptimeMillis() <= deadline) {
            if (list.itemAnimator?.isRunning != true) {
                manager.scrollToPositionWithOffset(position, 0)
                uiController.loopMainThreadUntilIdle()
                val candidate = list.findViewHolderForAdapterPosition(position)?.itemView
                if (candidate != null && candidate.getGlobalVisibleRect(visible) && !visible.isEmpty) {
                    row = candidate
                }
            }
            if (row == null) uiController.loopMainThreadForAtLeast(POLL_MILLIS)
        }
        return checkNotNull(row) { "La fila \"$name\" no tiene área visible" }
    }

    private fun positionOf(
        list: RecyclerView,
        name: String,
    ): Int {
        val adapter = list.adapter as? PerformerAdapter
        checkNotNull(adapter) { "La lista de artistas no tiene adaptador" }
        val position = adapter.currentList.indexOfFirst { it.name == name }
        check(position >= 0) { "La fila \"$name\" no está en la lista" }
        return position
    }

    fun assertErrorShown(): PerformerListRobot {
        waitForDisplayed(withText(R.string.artist_list_error_title))
        waitForDisplayed(withId(R.id.retryButton))
        return this
    }

    fun tapRetry(): PerformerListRobot {
        onView(withId(R.id.retryButton)).perform(click())
        return this
    }

    fun assertEmptyMessage(
        @StringRes messageRes: Int,
    ): PerformerListRobot {
        waitForDisplayed(withText(messageRes))
        return this
    }

    private fun folded(text: String): String =
        text.map { if (it in 'A'..'Z') it.lowercaseChar() else it }.joinToString("")

    private fun textOf(
        root: View,
        @IdRes id: Int,
    ): String? = root.findViewById<TextView>(id)?.text?.toString()

    /** La lista cuyo adaptador cumple [condition] sobre sus filas actuales. */
    private fun hasItems(condition: (List<PerformerItem>) -> Boolean): Matcher<View> =
        object : BoundedMatcher<View, RecyclerView>(RecyclerView::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("lista de artistas que cumple la condición")
            }

            override fun matchesSafely(list: RecyclerView): Boolean {
                val adapter = list.adapter as? PerformerAdapter ?: return false
                return condition(adapter.currentList)
            }
        }

    /** La fila de [name] con sus textos visibles. [summary] nulo no se comprueba. */
    private fun hasRow(
        name: String,
        overline: String,
        summary: String?,
    ): Matcher<View> =
        object : BoundedMatcher<View, RecyclerView>(RecyclerView::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("fila de \"$name\" con \"$overline\" y \"$summary\"")
            }

            override fun matchesSafely(list: RecyclerView): Boolean {
                val adapter = list.adapter as? PerformerAdapter ?: return false
                val position = adapter.currentList.indexOfFirst { it.name == name }
                if (position < 0) return false
                val row = list.findViewHolderForAdapterPosition(position)?.itemView ?: return false
                return textOf(row, R.id.performerName) == name &&
                    textOf(row, R.id.performerOverline) == overline &&
                    (summary == null || textOf(row, R.id.performerSummary) == summary)
            }
        }
}
