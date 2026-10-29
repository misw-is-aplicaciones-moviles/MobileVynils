package co.edu.uniandes.vinilos.ui

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * HU01-03: el catálogo contra BackVynils local (10.0.2.2:3000 con la semilla).
 * El visitante no ve el botón "+"; el coleccionista lo ve y su toque avisa que crear llega después.
 */
@RunWith(AndroidJUnit4::class)
class AlbumListScreenTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun visitor_sees_the_alphabetical_catalog_without_the_create_button() {
        onView(withText(R.string.role_visitor)).perform(click())

        waitForDisplayed(withId(R.id.albumList))
        onView(withId(R.id.albumList)).check(matches(hasTitleAt(position = 0, title = "A Day at the Races")))
        onView(withText("A Day at the Races")).check(matches(isDisplayed()))
        onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())))
    }

    @Test
    fun collector_sees_the_create_button_and_it_announces_the_pending_screen() {
        onView(withText(R.string.role_collector)).perform(click())

        waitForDisplayed(withId(R.id.albumList))
        onView(withId(R.id.primaryAction)).check(matches(isDisplayed()))

        onView(withId(R.id.primaryAction)).perform(click())

        waitForDisplayed(withText(R.string.album_create_pending))
    }

    /** El título de la fila [position], leído del ViewHolder ya enlazado. */
    private fun hasTitleAt(
        position: Int,
        title: String,
    ): Matcher<View> =
        object : BoundedMatcher<View, RecyclerView>(RecyclerView::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("RecyclerView con \"$title\" en la fila $position")
            }

            override fun matchesSafely(list: RecyclerView): Boolean {
                val holder = list.findViewHolderForAdapterPosition(position) ?: return false
                return holder.itemView
                    .findViewById<TextView>(R.id.albumTitle)
                    .text
                    .toString() == title
            }
        }
}
