package co.edu.uniandes.vinilos.robots

import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.FailureHandler
import androidx.test.espresso.PerformException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import co.edu.uniandes.vinilos.ui.collectors.CollectorAdapter
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.fail
import java.util.Locale

private const val POLL_MILLIS = 200L
private const val ROW_TIMEOUT_MILLIS = 15_000L

// Los sondeos de las filas fallan a propósito varias veces: con el manejador por defecto cada fallo guarda una captura,
// y en API 21 esas capturas repetidas congelan el emulador.
private val rethrow = FailureHandler { error, _ -> throw error }

/** Page Object de la lista de coleccionistas: abrir la pestaña, leer filas, refrescar y los estados. */
class CollectorListRobot {
    /** Elige el rol en el selector y espera el catálogo, que es donde se abre la app. */
    fun chooseRole(
        @StringRes roleRes: Int,
    ): CollectorListRobot {
        onView(withText(roleRes)).perform(click())
        waitForDisplayed(withId(R.id.albumList))
        return this
    }

    /** Toca la pestaña Coleccionistas de la barra inferior sin esperar la lista, para los casos con error. */
    fun openCollectorsTab(): CollectorListRobot {
        onView(allOf(withId(R.id.collectorListFragment), isDescendantOfA(withId(R.id.bottomNav)))).perform(click())
        return this
    }

    fun waitForList(): CollectorListRobot {
        waitForDisplayed(withId(R.id.collectorList))
        return this
    }

    /** Elige el rol, abre la pestaña Coleccionistas y espera la lista. */
    fun enterAs(
        @StringRes roleRes: Int,
    ): CollectorListRobot = chooseRole(roleRes).openCollectorsTab().waitForList()

    /** Busca la fila por nombre y comprueba número, iniciales y correo; [summary] nulo no se comprueba. */
    fun assertRow(
        name: String,
        initials: String,
        email: String,
        summary: String? = null,
    ): CollectorListRobot {
        scrollTo(name)
        onView(withId(R.id.collectorList)).check(matches(hasRow(name, initials, email, summary)))
        return this
    }

    /** Espera a que la lista tenga a [name] (llega de Room o de la red) y la desplaza hasta verlo. */
    fun scrollTo(name: String): CollectorListRobot {
        val deadline = System.currentTimeMillis() + ROW_TIMEOUT_MILLIS
        while (true) {
            try {
                onView(withId(R.id.collectorList))
                    .withFailureHandler(rethrow)
                    .perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(name))))
                return this
            } catch (error: PerformException) {
                if (System.currentTimeMillis() > deadline) fail("La fila \"$name\" no llegó a la lista: $error")
                Thread.sleep(POLL_MILLIS)
            }
        }
    }

    /** El orden de toda la lista, aunque haya coleccionistas ajenos a la prueba: por nombre según SQLite `COLLATE NOCASE`. */
    fun assertAlphabeticalOrder(): CollectorListRobot {
        onView(withId(R.id.collectorList)).check(matches(hasNamesInOrder()))
        return this
    }

    fun assertCreateButtonHidden(): CollectorListRobot {
        onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())))
        return this
    }

    fun pullToRefresh(): CollectorListRobot {
        onView(withId(R.id.collectorList))
            .perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(0))
        onView(withId(R.id.swipeRefresh)).perform(swipeDown())
        return this
    }

    fun tapRow(name: String): CollectorListRobot {
        scrollTo(name)
        onView(withId(R.id.collectorList))
            .perform(RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(hasDescendant(withText(name)), click()))
        return this
    }

    fun assertErrorShown(): CollectorListRobot {
        waitForDisplayed(withText(R.string.collector_list_error_title))
        waitForDisplayed(withId(R.id.retryButton))
        return this
    }

    fun tapRetry(): CollectorListRobot {
        onView(withId(R.id.retryButton)).perform(click())
        return this
    }

    // NOCASE ignora mayúsculas ASCII; conserva las letras acentuadas.
    private fun folded(text: String): String =
        text.map { if (it in 'A'..'Z') it.lowercaseChar() else it }.joinToString("")

    private fun textOf(
        root: View,
        @IdRes id: Int,
    ): String? = root.findViewById<TextView>(id)?.text?.toString()

    private fun hasNamesInOrder(): Matcher<View> =
        object : BoundedMatcher<View, RecyclerView>(RecyclerView::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("lista de coleccionistas por nombre")
            }

            override fun matchesSafely(list: RecyclerView): Boolean {
                val adapter = list.adapter as? CollectorAdapter ?: return false
                val names = adapter.currentList.map { it.name }
                return names.isNotEmpty() && names == names.sortedBy { folded(it) }
            }
        }

    /** La fila de [name] con sus textos visibles y el número de su posición actual en la lista. */
    private fun hasRow(
        name: String,
        initials: String,
        email: String,
        summary: String?,
    ): Matcher<View> =
        object : BoundedMatcher<View, RecyclerView>(RecyclerView::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("fila de \"$name\" con \"$initials\", \"$email\" y \"$summary\"")
            }

            override fun matchesSafely(list: RecyclerView): Boolean {
                val adapter = list.adapter as? CollectorAdapter ?: return false
                val position = adapter.currentList.indexOfFirst { it.name == name }
                if (position < 0) return false
                val row = list.findViewHolderForAdapterPosition(position)?.itemView ?: return false
                return textOf(row, R.id.collectorNumber) == String.format(Locale.ROOT, "%02d", position + 1) &&
                    textOf(row, R.id.collectorInitials) == initials &&
                    textOf(row, R.id.collectorName) == name &&
                    textOf(row, R.id.collectorEmail) == email &&
                    (summary == null || textOf(row, R.id.collectorSummary) == summary)
            }
        }
}
