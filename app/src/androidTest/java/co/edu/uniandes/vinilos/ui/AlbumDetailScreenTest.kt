package co.edu.uniandes.vinilos.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Minimal HU02-03 check against BackVynils with its seed, through the actual catalog card. */
@RunWith(AndroidJUnit4::class)
class AlbumDetailScreenTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun catalog_card_opens_its_detail_and_returns_without_collector_actions() {
        onView(withText(R.string.role_collector)).perform(click())
        waitForDisplayed(withText("Buscando América"))
        onView(withId(R.id.albumList)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                hasDescendant(withText("Buscando América")),
                click(),
            ),
        )

        waitForDisplayed(withId(R.id.albumName)).check(matches(withText("Buscando América")))
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.album_detail_title)))
        onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())))
        capture("hu02-03-header.png")

        onView(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText("Decisiones"))),
        )
        onView(withText("Decisiones")).check(matches(isDisplayed()))
        onView(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withId(R.id.commentAuthor))),
        )
        onView(withId(R.id.commentAuthor))
            .check(matches(withText(R.string.album_detail_comment_author)))
            .check(matches(isDisplayed()))
        capture("hu02-03-comments.png")

        onView(withId(R.id.headerBack)).perform(click())
        waitForDisplayed(withText("Buscando América"))
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.album_list_title)))
        onView(withId(R.id.primaryAction)).check(matches(isDisplayed()))
    }

    private fun capture(name: String) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP ||
            InstrumentationRegistry.getArguments().getString("captureScreenshots") != "true"
        ) {
            return
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(SCREENSHOT_IDLE_MILLIS, SCREENSHOT_TIMEOUT_MILLIS)
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.filesDir, name).outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
        }
        screenshot.recycle()
    }

    private companion object {
        const val PNG_QUALITY = 100
        const val SCREENSHOT_IDLE_MILLIS = 100L
        const val SCREENSHOT_TIMEOUT_MILLIS = 3_000L
    }
}
