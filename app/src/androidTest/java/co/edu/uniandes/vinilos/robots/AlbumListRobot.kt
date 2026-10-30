package co.edu.uniandes.vinilos.robots

import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.PerformException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.fail

private const val POLL_MILLIS = 200L
private const val ROW_TIMEOUT_MILLIS = 15_000L

/** Page Object del catálogo: elegir rol, leer filas, desplazarse, refrescar y el botón "+". */
class AlbumListRobot {
    fun enterAs(
        @StringRes roleRes: Int,
    ): AlbumListRobot {
        onView(withText(roleRes)).perform(click())
        waitForDisplayed(withId(R.id.albumList))
        return this
    }

    /** Fila [position] ya enlazada: número, título, artista y "Género · Año". */
    fun assertRow(
        position: Int,
        number: String,
        title: String,
        artist: String,
        meta: String,
    ): AlbumListRobot {
        onView(withId(R.id.albumList)).check(matches(hasRowAt(position, number, title, artist, meta)))
        return this
    }

    fun assertTitles(vararg titles: String): AlbumListRobot {
        titles.forEachIndexed { position, title ->
            // La fila debe estar enlazada para leerla: se desplaza la lista hasta ella.
            onView(withId(R.id.albumList))
                .perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(position))
                .check(matches(hasRowAt(position, title = title)))
        }
        return this
    }

    /** Desplaza la lista hasta la fila de [title]; espera a que el catálogo la reciba si aún no llegó. */
    fun scrollTo(title: String): AlbumListRobot {
        val deadline = System.currentTimeMillis() + ROW_TIMEOUT_MILLIS
        while (true) {
            try {
                onView(withId(R.id.albumList))
                    .perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(title))))
                return this
            } catch (error: PerformException) {
                if (System.currentTimeMillis() > deadline) fail("La fila \"$title\" no llegó al catálogo: $error")
                Thread.sleep(POLL_MILLIS)
            }
        }
    }

    /** Fila de [title] con [artist] como subtítulo; desplaza la lista hasta ella. */
    fun assertArtistOf(
        title: String,
        artist: String,
    ): AlbumListRobot {
        scrollTo(title)
        onView(allOf(withId(R.id.albumSubtitle), hasSibling(title))).check(matches(withText(artist)))
        return this
    }

    fun assertShown(title: String): AlbumListRobot {
        onView(withText(title)).check(matches(isDisplayed()))
        return this
    }

    fun pullToRefresh(): AlbumListRobot {
        onView(withId(R.id.swipeRefresh)).perform(swipeDown())
        return this
    }

    fun assertCreateButtonShown(): AlbumListRobot = assertPrimaryAction(shown = true)

    fun assertCreateButtonHidden(): AlbumListRobot = assertPrimaryAction(shown = false)

    fun tapCreate(): AlbumListRobot {
        onView(withId(R.id.primaryAction)).perform(click())
        return this
    }

    private fun assertPrimaryAction(shown: Boolean): AlbumListRobot {
        val visibility = if (shown) isDisplayed() else not(isDisplayed())
        onView(withId(R.id.primaryAction)).check(matches(visibility))
        return this
    }

    /** El subtítulo cuya fila tiene [title] como título. */
    private fun hasSibling(title: String): Matcher<View> =
        object : BoundedMatcher<View, TextView>(TextView::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("subtítulo de la fila \"$title\"")
            }

            override fun matchesSafely(view: TextView): Boolean {
                val row = rowOf(view) ?: return false
                return textOf(row, R.id.albumTitle) == title
            }
        }

    private fun rowOf(view: View): View? {
        var current: View? = view
        while (current != null && current.id != R.id.albumRow) current = current.parent as? View
        return current
    }

    private fun textOf(
        root: View,
        @IdRes id: Int,
    ): String? = root.findViewById<TextView>(id)?.text?.toString()

    private fun hasRowAt(
        position: Int,
        number: String? = null,
        title: String,
        artist: String? = null,
        meta: String? = null,
    ): Matcher<View> =
        object : BoundedMatcher<View, RecyclerView>(RecyclerView::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("catálogo con \"$title\" en la fila $position")
            }

            override fun matchesSafely(list: RecyclerView): Boolean {
                val row = list.findViewHolderForAdapterPosition(position)?.itemView ?: return false
                return textOf(row, R.id.albumTitle) == title &&
                    (number == null || textOf(row, R.id.albumNumber) == number) &&
                    (artist == null || textOf(row, R.id.albumSubtitle) == artist) &&
                    (meta == null || textOf(row, R.id.albumMeta) == meta)
            }
        }
}
