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
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher

/** Page Object del selector de álbum: búsqueda, fila, álbum ya agregado y continuar. */
class CollectorAlbumPickerRobot {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    fun waitUntilOpen(collectorName: String): CollectorAlbumPickerRobot {
        waitForDisplayed(
            allOf(withId(R.id.headerTitle), withText(R.string.collector_album_picker_title), isDisplayed()),
        )
        val contextText = context.getString(R.string.collector_album_picker_context, collectorName)
        waitForDisplayed(allOf(withId(R.id.pickerContext), withText(contextText), isDisplayed()))
        return this
    }

    fun search(query: String): CollectorAlbumPickerRobot {
        onView(withId(R.id.searchField)).perform(replaceText(query), closeSoftKeyboard())
        return this
    }

    fun assertSearch(query: String): CollectorAlbumPickerRobot {
        onView(allOf(withId(R.id.searchField), withText(query), isDisplayed())).check(matches(isDisplayed()))
        return this
    }

    fun assertNoResults(): CollectorAlbumPickerRobot {
        waitForDisplayed(allOf(withId(R.id.emptyMessage), withText(R.string.collector_album_picker_no_results)))
        return this
    }

    fun assertRow(
        name: String,
        meta: String,
    ): CollectorAlbumPickerRobot {
        awaitRow(name) { onView(focusedList()).check(showsMeta(name, meta)) }
        return this
    }

    /** Espera la fila: tras una búsqueda, las filas se calculan fuera del hilo principal y llegan después. */
    fun select(name: String): CollectorAlbumPickerRobot {
        awaitRow(name) { onView(focusedList()).perform(choose(name)) }
        return this
    }

    fun assertAlreadyAdded(name: String): CollectorAlbumPickerRobot {
        awaitRow(name) { onView(focusedList()).check(showsAdded(name)) }
        return this
    }

    fun assertContinueDisabled(): CollectorAlbumPickerRobot {
        onView(withId(R.id.continueButton)).check(
            matches(allOf(isDisplayed(), not(isEnabled()), withText(R.string.collector_album_picker_select))),
        )
        return this
    }

    fun assertContinue(albumName: String): CollectorAlbumPickerRobot {
        val label = context.getString(R.string.collector_album_picker_continue, albumName)
        onView(withId(R.id.continueButton)).check(matches(allOf(isDisplayed(), isEnabled(), withText(label))))
        return this
    }

    fun tapContinue(): CollectorAlbumPickerRobot {
        onView(withId(R.id.continueButton)).perform(click())
        return this
    }

    fun tapClose(): CollectorAlbumPickerRobot {
        val close = allOf(withId(R.id.headerClose), isDisplayingAtLeast(VISIBLE_PORTION))
        waitForDisplayed(close)
        onView(close).perform(click())
        return this
    }

    fun assertNetworkError(): CollectorAlbumPickerRobot {
        waitForDisplayed(withText(R.string.state_error_title))
        waitForDisplayed(withText(R.string.collector_list_error_body))
        waitForDisplayed(withId(R.id.retryButton))
        return this
    }

    fun tapRetry(): CollectorAlbumPickerRobot {
        onView(allOf(withId(R.id.retryButton), hasWindowFocus())).perform(pressRetry())
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

    private fun focusedList() = allOf(withId(R.id.albumList), hasWindowFocus())

    private fun scrollToRow(name: String) =
        RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
            hasDescendant(allOf(withId(R.id.albumTitle), withText(name))),
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

    private fun showsMeta(
        name: String,
        meta: String,
    ): ViewAssertion =
        ViewAssertion { view, error ->
            if (error != null) throw error
            val row = rowChild(view as RecyclerView, name)
            val artist = context.getString(R.string.album_no_artist)
            val subtitle = row.findViewById<TextView>(R.id.albumSubtitle).text.toString()
            val shownMeta = row.findViewById<TextView>(R.id.albumMeta).text.toString()
            check(subtitle == artist) { "El artista de $name no es $artist" }
            check(shownMeta == meta) { "El género de $name no es $meta" }
        }

    private fun showsAdded(name: String): ViewAssertion =
        ViewAssertion { view, error ->
            if (error != null) throw error
            val row = rowChild(view as RecyclerView, name)
            val chip = row.findViewById<TextView>(R.id.addedChip)
            val radio = row.findViewById<View>(R.id.radioIcon)
            val expected = context.getString(R.string.collector_album_picker_already_added)
            check(chip.visibility == View.VISIBLE && chip.text.toString() == expected) {
                "La fila $name no muestra el aviso de ya agregado"
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
                list.getChildAt(index).findViewById<TextView>(R.id.albumTitle)?.text
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
                    child.findViewById<TextView>(R.id.albumTitle)?.text?.toString() == name
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
        const val ROW_TIMEOUT_MILLIS = 2_000L
    }
}
