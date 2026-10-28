package co.edu.uniandes.vinilos.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** TR-06: la app abre en el selector y cada rol entra al catálogo sin la barra inferior. */
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
}
