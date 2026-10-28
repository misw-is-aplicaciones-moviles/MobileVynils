package co.edu.uniandes.vinilos.ui

import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.VinilosApplication
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TR-06: la app abre en el selector y cada rol entra al catálogo sin la barra inferior.
 * Si el proceso se restaura sin rol, vuelve al selector.
 */
@RunWith(AndroidJUnit4::class)
class RoleSelectTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun role_selector_is_the_start_screen_without_the_bottom_bar() {
        onView(withText(R.string.role_question)).check(matches(isDisplayed()))
        onView(withText(R.string.role_visitor)).check(matches(isDisplayed()))
        onView(withText(R.string.role_collector)).check(matches(isDisplayed()))
        onView(withText(R.string.role_change_hint)).check(matches(isDisplayed()))
        onView(withText(R.string.role_title)).check(matches(isDisplayed()))
        onView(withId(R.id.artworkDisc)).check(matches(isDisplayed()))
        onView(withId(R.id.bottomNav)).check(matches(not(isDisplayed())))
    }

    @Test
    fun choosing_visitor_opens_the_catalog() {
        onView(withText(R.string.role_visitor)).perform(click())

        onView(allOf(withId(R.id.headerTitle), isDisplayed())).check(matches(withText(R.string.album_list_title)))
        onView(withId(R.id.bottomNav)).check(matches(isDisplayed()))
    }

    @Test
    fun choosing_collector_opens_the_catalog() {
        onView(withText(R.string.role_collector)).perform(click())

        onView(allOf(withId(R.id.headerTitle), isDisplayed())).check(matches(withText(R.string.album_list_title)))
        onView(withId(R.id.bottomNav)).check(matches(isDisplayed()))
    }

    @Test
    fun rotation_keeps_the_catalog() {
        onView(withText(R.string.role_visitor)).perform(click())

        activityRule.scenario.recreate()

        onView(allOf(withId(R.id.headerTitle), isDisplayed())).check(matches(withText(R.string.album_list_title)))
        onView(withId(R.id.bottomNav)).check(matches(isDisplayed()))
    }

    @Test
    fun restored_catalog_without_a_role_returns_to_the_selector() {
        onView(withText(R.string.role_collector)).perform(click())
        onView(allOf(withId(R.id.performerListFragment), isDescendantOfA(withId(R.id.bottomNav)))).perform(click())
        forgetRole()

        activityRule.scenario.recreate()

        onView(withText(R.string.role_question)).check(matches(isDisplayed()))
        onView(withId(R.id.bottomNav)).check(matches(not(isDisplayed())))
        pressBackUnconditionally()
        assertEquals(Lifecycle.State.DESTROYED, activityRule.scenario.state)
    }

    /** El rol no viaja en el estado guardado. Borrarlo y recrear la actividad imita la muerte del proceso. */
    private fun forgetRole() {
        activityRule.scenario.onActivity { activity ->
            (activity.application as VinilosApplication).container.sessionRepository.clearRole()
        }
    }
}
