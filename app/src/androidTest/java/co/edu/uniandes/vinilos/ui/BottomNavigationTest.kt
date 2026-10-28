package co.edu.uniandes.vinilos.ui

import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** TR-01: la barra inferior cambia entre las tres pestañas y "atrás" vuelve al catálogo. */
@RunWith(AndroidJUnit4::class)
class BottomNavigationTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private fun enterAsVisitor() {
        onView(withText(R.string.role_visitor)).perform(click())
    }

    private fun tapTab(
        @IdRes itemId: Int,
    ) {
        onView(allOf(withId(itemId), isDescendantOfA(withId(R.id.bottomNav)))).perform(click())
    }

    private fun assertTitle(
        @StringRes titleRes: Int,
    ) {
        onView(allOf(withId(R.id.headerTitle), isDisplayed())).check(matches(withText(titleRes)))
    }

    @Test
    fun bottom_bar_switches_between_the_three_tabs() {
        enterAsVisitor()
        assertTitle(R.string.album_list_title)

        tapTab(R.id.performerListFragment)
        assertTitle(R.string.artist_list_title)

        tapTab(R.id.collectorListFragment)
        assertTitle(R.string.collector_list_title)

        tapTab(R.id.albumListFragment)
        assertTitle(R.string.album_list_title)
    }

    @Test
    fun back_from_a_tab_returns_to_the_catalog() {
        enterAsVisitor()
        tapTab(R.id.performerListFragment)
        assertTitle(R.string.artist_list_title)

        pressBack()

        assertTitle(R.string.album_list_title)
        onView(withId(R.id.bottomNav)).check(matches(hasSelectedItem(R.id.albumListFragment)))
    }

    /** La pestaña marcada en la barra, según su menú (no según la vista interna del ítem). */
    private fun hasSelectedItem(
        @IdRes itemId: Int,
    ): Matcher<View> =
        object : BoundedMatcher<View, BottomNavigationView>(BottomNavigationView::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("BottomNavigationView con el ítem $itemId seleccionado")
            }

            override fun matchesSafely(view: BottomNavigationView): Boolean = view.selectedItemId == itemId
        }
}
