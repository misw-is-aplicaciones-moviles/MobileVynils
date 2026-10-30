package co.edu.uniandes.vinilos.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** HU02 flows through actual catalog cards and the local BackVynils backend. */
@RunWith(AndroidJUnit4::class)
class AlbumDetailScreenTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun collector_can_create_track_from_album_detail_and_return_to_catalog() {
        openAlbum(roleRes = R.string.role_collector)

        waitForDisplayed(withId(R.id.detailList))
        onView(withId(R.id.albumName)).check(matches(withText("Buscando América")))
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.album_detail_title)))
        onView(withId(R.id.albumArtist)).check(matches(withText("Rubén Blades Bellido de Luna")))
        onView(allOf(withId(R.id.infoValue), isDescendantOfA(withId(R.id.labelInfo))))
            .check(matches(withText("Elektra")))
        onView(allOf(withId(R.id.infoValue), isDescendantOfA(withId(R.id.dateInfo))))
            .check(matches(withText(containsString("1984"))))
        var existingTrackCount = 0
        activityRule.scenario.onActivity { activity ->
            existingTrackCount =
                activity
                    .findViewById<View>(R.id.tracksInfo)
                    .findViewById<TextView>(R.id.infoValue)
                    .text
                    .toString()
                    .toInt()
        }
        onView(allOf(withId(R.id.infoValue), isDescendantOfA(withId(R.id.tracksInfo))))
            .check(matches(withText(existingTrackCount.toString())))
        onView(withId(R.id.primaryAction)).check(matches(isDisplayed()))
        capture("hu02-03-header.png")

        onView(withId(R.id.primaryAction)).perform(click())
        waitForDisplayed(withId(R.id.sheetTitle)).check(matches(withText(R.string.track_sheet_title)))
        val nextTrackNumber = (existingTrackCount + 1).toString().padStart(2, '0')
        onView(withId(R.id.sheetSubtitle))
            .check(matches(withText("Buscando América · canción $nextTrackNumber")))
        val createdTrackName = "OpenCode${System.currentTimeMillis()}"
        onView(withId(R.id.trackNameField)).perform(replaceText(createdTrackName))
        onView(withId(R.id.trackNameField)).check(matches(withText(createdTrackName)))
        onView(withId(R.id.key0)).perform(click())
        onView(withId(R.id.key5)).perform(click())
        onView(withId(R.id.key5)).perform(click())
        onView(withId(R.id.key5)).perform(click())
        onView(withId(R.id.trackDuration)).check(matches(withText("05:55")))
        onView(withId(R.id.saveTrack)).perform(click())
        waitForDisplayed(withText(R.string.track_created))
        onView(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(createdTrackName))),
        )
        onView(withText(createdTrackName)).check(matches(isDisplayed()))

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

    @Test
    fun visitor_returns_to_catalog_with_system_back() {
        openAlbum()
        waitForDisplayed(withId(R.id.detailList))
        onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())))

        pressBack()

        waitForDisplayed(withText("Buscando América"))
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.album_list_title)))
        onView(withId(R.id.albumListFragment)).check(matches(isSelected()))
        onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())))
    }

    @Test
    fun rotation_preserves_detail_and_reselecting_albums_returns_to_catalog() {
        openAlbum()
        waitForDisplayed(withId(R.id.detailList))
        var previous = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        var target = Configuration.ORIENTATION_LANDSCAPE
        activityRule.scenario.onActivity { activity ->
            previous = activity.requestedOrientation
            val portrait = activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            target = if (portrait) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            activity.requestedOrientation =
                if (portrait) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        try {
            waitForDisplayed(allOf(withId(R.id.headerTitle), inOrientation(target)))
                .check(matches(withText(R.string.album_detail_title)))
            onView(withId(R.id.albumName)).check(matches(withText("Buscando América")))
            onView(withId(R.id.albumListFragment)).check(matches(isSelected()))
            onView(withId(R.id.albumListFragment)).perform(click())
            waitForDisplayed(withText("Buscando América"))
            onView(withId(R.id.headerTitle)).check(matches(withText(R.string.album_list_title)))
        } finally {
            activityRule.scenario.onActivity { it.requestedOrientation = previous }
        }
    }

    @Test
    fun empty_tracks_and_comments_keep_the_album_visible() {
        openAlbum(title = "Poeta del pueblo")
        waitForDisplayed(withId(R.id.detailList))
        onView(withId(R.id.albumName)).check(matches(withText("Poeta del pueblo")))

        scrollToMessage(R.string.album_detail_no_tracks)
        scrollToMessage(R.string.album_detail_no_comments)

        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.album_detail_title)))
    }

    private fun openAlbum(
        title: String = "Buscando América",
        roleRes: Int = R.string.role_visitor,
    ) {
        onView(withText(roleRes)).perform(click())
        waitForDisplayed(withText(title))
        onView(withId(R.id.albumList)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(hasDescendant(withText(title)), click()),
        )
    }

    private fun scrollToMessage(textRes: Int) {
        onView(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(withText(textRes)),
        )
        onView(withText(textRes)).check(matches(isDisplayed()))
    }

    private fun inOrientation(orientation: Int): Matcher<View> =
        object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("orientation $orientation")
            }

            override fun matchesSafely(view: View): Boolean = view.resources.configuration.orientation == orientation
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
