package co.edu.uniandes.vinilos.ui

import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** TR-04: cabecera con chip, tarjeta de álbum y estados de carga, error y vacío. */
@RunWith(AndroidJUnit4::class)
class SharedComponentsTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun catalog_shows_the_role_chip_the_card_and_the_three_states() {
        onView(withText(R.string.role_visitor)).perform(click())

        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.album_list_title)))
        onView(withId(R.id.roleChip)).check(matches(allOf(isDisplayed(), withText(R.string.role_visitor))))
        onView(withId(R.id.roleChip)).check(matches(withContentDescription(chipDescription(R.string.role_visitor))))
        onView(allOf(withId(R.id.previewMessage), isDisplayed())).check(matches(withText(R.string.pending_this_sprint)))
        onView(withText(R.string.sample_album_name)).check(matches(isDisplayed()))
        onView(withText(R.string.sample_album_artist)).check(matches(isDisplayed()))
        onView(withText(R.string.sample_album_meta)).check(matches(isDisplayed()))
        onView(withText(R.string.state_loading)).check(matches(isDisplayed()))
        onView(withText(R.string.state_error_title)).check(matches(isDisplayed()))
        onView(withText(R.string.state_error_body)).check(matches(isDisplayed()))
        onView(withText(R.string.state_empty_message)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.headerBack)).check(matches(not(isDisplayed())))
        onView(withId(R.id.headerClose)).check(matches(not(isDisplayed())))
    }

    @Test
    fun retry_says_the_reload_arrives_with_the_catalog() {
        onView(withText(R.string.role_collector)).perform(click())

        onView(withId(R.id.retryButton)).perform(click())

        onView(withText(R.string.state_retry_later)).check(matches(isDisplayed()))
    }

    @Test
    fun role_chip_returns_to_the_selector_and_back_leaves_the_app() {
        onView(withText(R.string.role_visitor)).perform(click())
        leaveFromCurrentTab()

        onView(withText(R.string.role_collector)).perform(click())
        onView(allOf(withId(R.id.performerListFragment), isDescendantOfA(withId(R.id.bottomNav)))).perform(click())
        onView(withId(R.id.componentPreview)).check(matches(not(isDisplayed())))
        onView(allOf(withId(R.id.pendingMessage), isDisplayed())).check(matches(withText(R.string.pending_next_sprint)))
        onView(withId(R.id.roleChip)).check(matches(withText(R.string.role_collector)))
        leaveFromCurrentTab()

        onView(withText(R.string.role_visitor)).perform(click())
        onView(allOf(withId(R.id.collectorListFragment), isDescendantOfA(withId(R.id.bottomNav)))).perform(click())
        leaveFromCurrentTab()

        pressBackUnconditionally()
        assertEquals(Lifecycle.State.DESTROYED, activityRule.scenario.state)
    }

    private fun leaveFromCurrentTab() {
        onView(withId(R.id.roleChip)).perform(click())
        onView(withText(R.string.role_question)).check(matches(isDisplayed()))
        onView(withId(R.id.bottomNav)).check(matches(not(isDisplayed())))
    }

    private fun chipDescription(roleRes: Int): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.getString(R.string.role_chip_action, context.getString(roleRes))
    }
}
