package co.edu.uniandes.vinilos.robots

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.hasAccessibilityHint
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher

/** Page Object del selector de artista favorito: búsqueda, fila, ya favorito y agregar. */
class CollectorFavoritePickerRobot {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    fun waitUntilOpen(collectorName: String): CollectorFavoritePickerRobot {
        waitForDisplayed(
            allOf(withId(R.id.headerTitle), withText(R.string.collector_favorite_picker_title), isDisplayed()),
        )
        val contextText = context.getString(R.string.collector_favorite_picker_context, collectorName)
        waitForDisplayed(allOf(withId(R.id.pickerContext), withText(contextText), isDisplayed()))
        return this
    }

    fun search(query: String): CollectorFavoritePickerRobot {
        onView(withId(R.id.searchField)).perform(replaceText(query), closeSoftKeyboard())
        return this
    }

    fun assertSearch(query: String): CollectorFavoritePickerRobot {
        onView(allOf(withId(R.id.searchField), withText(query), isDisplayed())).check(matches(isDisplayed()))
        // Con texto escrito, TalkBack sigue diciendo qué busca el campo (T7).
        onView(withId(R.id.searchField))
            .check(matches(hasAccessibilityHint(R.string.collector_favorite_picker_search_hint)))
        return this
    }

    fun assertNoResults(): CollectorFavoritePickerRobot {
        waitForDisplayed(
            allOf(withId(R.id.emptyMessage), withText(R.string.collector_favorite_picker_no_results)),
        )
        return this
    }

    fun assertRow(
        name: String,
        overline: String,
    ): CollectorFavoritePickerRobot {
        awaitRow(name) { onView(focusedList()).check(showsOverline(name, overline)) }
        return this
    }

    /** La lista filtrada tiene una sola fila y es [name]. */
    fun assertOnly(name: String): CollectorFavoritePickerRobot {
        awaitRow(name) { onView(focusedList()).check(onlyRow(name)) }
        return this
    }

    fun select(name: String): CollectorFavoritePickerRobot {
        awaitRow(name) { onView(focusedList()).perform(choose(name)) }
        return this
    }

    /** La fila elegida queda marcada para TalkBack (`isSelected`), no solo con el fondo. */
    fun assertSelected(name: String): CollectorFavoritePickerRobot {
        val row =
            allOf(
                withId(R.id.favoriteRow),
                hasDescendant(allOf(withId(R.id.performerName), withText(name))),
                isDescendantOfA(focusedList()),
            )
        awaitRow(name) { onView(row).check(matches(isSelected())) }
        return this
    }

    fun assertAlreadyAdded(name: String): CollectorFavoritePickerRobot {
        awaitRow(name) { onView(focusedList()).check(showsAdded(name)) }
        return this
    }

    fun assertSelectPrompt(): CollectorFavoritePickerRobot {
        onView(withId(R.id.continueButton)).check(
            matches(
                allOf(isDisplayed(), not(isEnabled()), withText(R.string.collector_favorite_picker_select)),
            ),
        )
        return this
    }

    fun assertAdd(name: String): CollectorFavoritePickerRobot {
        val label = context.getString(R.string.collector_favorite_picker_add, name)
        onView(withId(R.id.continueButton)).check(matches(allOf(isDisplayed(), isEnabled(), withText(label))))
        return this
    }

    fun tapAdd(): CollectorFavoritePickerRobot {
        onView(withId(R.id.continueButton)).perform(click())
        return this
    }

    fun tapClose(): CollectorFavoritePickerRobot {
        val close = allOf(withId(R.id.headerClose), isDisplayingAtLeast(VISIBLE_PORTION))
        waitForDisplayed(close)
        onView(close).perform(click())
        return this
    }

    fun assertLoadError(): CollectorFavoritePickerRobot {
        waitForDisplayed(withText(R.string.state_error_title))
        waitForDisplayed(withText(R.string.collector_list_error_body))
        waitForDisplayed(withId(R.id.retryButton))
        return this
    }

    fun tapRetry(): CollectorFavoritePickerRobot {
        onView(allOf(withId(R.id.retryButton), hasWindowFocus())).perform(pressRetry())
        return this
    }

    fun assertSubmitNetworkError(): CollectorFavoritePickerRobot {
        waitForDisplayed(
            allOf(
                withId(R.id.submitError),
                withText(R.string.collector_album_add_network_error),
                isDisplayed(),
            ),
        )
        return this
    }

    private fun pressRetry(): ViewAction =
        object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)

            override fun getDescription() = "toca reintentar"

            override fun perform(
                uiController: UiController,
                view: View,
            ) {
                try {
                    scrollTo().perform(uiController, view)
                } catch (ignored: RuntimeException) {
                    uiController.loopMainThreadUntilIdle()
                }
                if (isDisplayingAtLeast(VISIBLE_PORTION).matches(view)) {
                    click().perform(uiController, view)
                } else {
                    view.performClick()
                }
            }
        }

    private fun awaitRow(
        name: String,
        checkRow: () -> Unit,
    ) {
        val deadline = System.currentTimeMillis() + ROW_TIMEOUT_MILLIS
        var last: RuntimeException? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(focusedList()).perform(scrollToRow(name))
                checkRow()
                return
            } catch (error: RuntimeException) {
                last = error
            }
            Thread.sleep(VISIBILITY_POLL_MILLIS)
        }
        throw last ?: error("No apareció la fila $name")
    }

    private fun focusedList() = allOf(withId(R.id.performerList), hasWindowFocus())

    private fun scrollToRow(name: String) =
        RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
            hasDescendant(allOf(withId(R.id.performerName), withText(name))),
        )

    /**
     * La fila mide más que el trozo visible en el Nexus 5 y Espresso exige ver el 90 % para tocarla.
     * Se ancla al inicio de la lista activa; si sigue cortada, se usa el listener de la fila.
     */
    private fun choose(name: String): ViewAction =
        object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(RecyclerView::class.java)

            override fun getDescription() = "elige la fila"

            override fun perform(
                uiController: UiController,
                view: View,
            ) {
                val list = view as RecyclerView
                val manager = list.layoutManager as LinearLayoutManager
                manager.scrollToPositionWithOffset(adapterPosition(list, name), 0)
                uiController.loopMainThreadUntilIdle()
                repeat(VISIBILITY_TRIES) {
                    val row = rowOrNull(list, name)
                    if (row != null && isDisplayingAtLeast(VISIBLE_PORTION).matches(row)) {
                        click().perform(uiController, row)
                        return
                    }
                    uiController.loopMainThreadForAtLeast(VISIBILITY_POLL_MILLIS)
                }
                rowChild(list, name).performClick()
            }
        }

    private fun showsOverline(
        name: String,
        overline: String,
    ): ViewAssertion =
        ViewAssertion { view, error ->
            if (error != null) throw error
            val row = rowChild(view as RecyclerView, name)
            val shown = row.findViewById<TextView>(R.id.performerOverline).text.toString()
            check(shown == overline) { "La sobrelínea de $name es \"$shown\" y se esperaba \"$overline\"" }
        }

    private fun onlyRow(name: String): ViewAssertion =
        ViewAssertion { view, error ->
            if (error != null) throw error
            val list = view as RecyclerView
            val count = list.adapter?.itemCount ?: 0
            check(count == 1) { "Se esperaba una fila y hay $count" }
            rowChild(list, name)
        }

    private fun showsAdded(name: String): ViewAssertion =
        ViewAssertion { view, error ->
            if (error != null) throw error
            val row = rowChild(view as RecyclerView, name)
            val chip = row.findViewById<TextView>(R.id.addedChip)
            val radio = row.findViewById<View>(R.id.radioIcon)
            val expected = context.getString(R.string.collector_favorite_picker_already)
            check(chip.visibility == View.VISIBLE && chip.text.toString() == expected) {
                "La fila $name no muestra el aviso de ya favorito"
            }
            check(radio.visibility != View.VISIBLE) { "La fila $name sigue mostrando el selector" }
        }

    private fun adapterPosition(
        list: RecyclerView,
        name: String,
    ): Int {
        val position = list.getChildAdapterPosition(rowChild(list, name))
        check(position != RecyclerView.NO_POSITION) { "La fila $name no está ligada a la lista" }
        return position
    }

    private fun rowChild(
        list: RecyclerView,
        name: String,
    ): View {
        rowOrNull(list, name)?.let { return it }
        val titles =
            (0 until list.childCount).map { index ->
                list.getChildAt(index).findViewById<TextView>(R.id.performerName)?.text
            }
        error("No está visible $name. Filas: $titles")
    }

    private fun rowOrNull(
        list: RecyclerView,
        name: String,
    ): View? {
        val matches =
            (0 until list.childCount)
                .map { list.getChildAt(it) }
                .filter { child ->
                    child.findViewById<TextView>(R.id.performerName)?.text?.toString() == name
                }
        val focused = matches.filter { it.hasWindowFocus() }
        return focused.lastOrNull() ?: matches.lastOrNull()
    }

    /** En API 21 conviven la ventana que pierde el foco y la nueva; hay que quedarse con la activa. */
    private fun hasWindowFocus(): Matcher<View> =
        object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("con el foco de la ventana")
            }

            override fun matchesSafely(view: View) = view.hasWindowFocus()
        }

    private companion object {
        const val VISIBLE_PORTION = 90
        const val VISIBILITY_TRIES = 10
        const val VISIBILITY_POLL_MILLIS = 200L
        const val ROW_TIMEOUT_MILLIS = 15_000L
    }
}
