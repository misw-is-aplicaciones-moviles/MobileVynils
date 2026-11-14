package co.edu.uniandes.vinilos.ui

import android.graphics.drawable.Drawable
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
import co.edu.uniandes.vinilos.ui.common.BottomBarHumpEdgeTreatment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.shape.MaterialShapeDrawable
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TR-01: la barra inferior cambia entre las tres pestañas y "atrás" vuelve al catálogo.
 * OPT-03: la joroba sigue al botón "+" y una Activity nueva vuelve a construirla.
 */
@RunWith(AndroidJUnit4::class)
class BottomNavigationTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private fun enterAs(
        @StringRes roleRes: Int,
    ) {
        onView(withText(roleRes)).perform(click())
    }

    private fun enterAsVisitor() {
        enterAs(R.string.role_visitor)
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

    @Test
    fun role_selector_hides_the_bottom_bar() {
        onView(withText(R.string.role_question)).check(matches(isDisplayed()))
        onView(withId(R.id.bottomBar)).check(matches(not(isDisplayed())))
    }

    @Test
    fun visitor_keeps_a_flat_bar_on_the_three_lists() {
        enterAsVisitor()
        assertFlatBar()

        tapTab(R.id.performerListFragment)
        assertTitle(R.string.artist_list_title)
        assertFlatBar()

        tapTab(R.id.collectorListFragment)
        assertTitle(R.string.collector_list_title)
        assertFlatBar()

        tapTab(R.id.albumListFragment)
        assertTitle(R.string.album_list_title)
        assertFlatBar()
    }

    @Test
    fun collector_hump_follows_the_catalog_and_a_new_activity_rebuilds_it() {
        enterAs(R.string.role_collector)
        assertTitle(R.string.album_list_title)
        assertHump()
        val hump = bottomBarBackground()

        tapTab(R.id.performerListFragment)
        assertTitle(R.string.artist_list_title)
        assertFlatBar()

        tapTab(R.id.collectorListFragment)
        assertTitle(R.string.collector_list_title)
        assertFlatBar()

        tapTab(R.id.albumListFragment)
        assertTitle(R.string.album_list_title)
        assertHump()
        assertSame(hump, bottomBarBackground())

        activityRule.scenario.recreate()

        assertTitle(R.string.album_list_title)
        assertHump()
        assertNotSame(hump, bottomBarBackground())
    }

    private fun assertFlatBar() {
        onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())))
        onView(withId(R.id.bottomBar)).check(matches(barShape(withHump = false)))
    }

    private fun assertHump() {
        onView(withId(R.id.primaryAction)).check(matches(isDisplayed()))
        onView(withId(R.id.bottomBar)).check(matches(barShape(withHump = true)))
    }

    private fun bottomBarBackground(): Drawable {
        lateinit var background: Drawable
        activityRule.scenario.onActivity { activity ->
            background = activity.findViewById<View>(R.id.bottomBar).background
        }
        return background
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

    /** La joroba es el borde superior del fondo, no solo la visibilidad del botón "+". */
    private fun barShape(withHump: Boolean): Matcher<View> =
        object : BoundedMatcher<View, View>(View::class.java) {
            override fun describeTo(description: Description) {
                description.appendText(if (withHump) "barra con joroba" else "barra plana")
            }

            override fun matchesSafely(view: View): Boolean {
                val drawable = view.background as? MaterialShapeDrawable ?: return false
                val hump = drawable.shapeAppearanceModel.topEdge is BottomBarHumpEdgeTreatment
                return hump == withHump
            }
        }
}
