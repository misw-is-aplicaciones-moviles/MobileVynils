package co.edu.uniandes.vinilos.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
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
import co.edu.uniandes.vinilos.BuildConfig
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** HU02 flows through actual catalog cards; controlled failures require the local E2E proxy. */
@RunWith(AndroidJUnit4::class)
class AlbumDetailScreenTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val proxyEnabled: Boolean
        get() = InstrumentationRegistry.getArguments().getString("hu02E2eProxy") == "true"

    @Before
    fun resetProxy() {
        if (proxyEnabled) proxyControl("reset")
    }

    @Test
    fun catalog_card_opens_its_detail_and_returns_without_collector_actions() {
        openAlbum(roleRes = R.string.role_collector)

        waitForDisplayed(withId(R.id.albumName)).check(matches(withText("Buscando América")))
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.album_detail_title)))
        onView(withId(R.id.albumArtist)).check(matches(withText("Rubén Blades Bellido de Luna")))
        onView(allOf(withId(R.id.infoValue), isDescendantOfA(withId(R.id.labelInfo))))
            .check(matches(withText("Elektra")))
        onView(allOf(withId(R.id.infoValue), isDescendantOfA(withId(R.id.dateInfo))))
            .check(matches(withText(containsString("1984"))))
        onView(allOf(withId(R.id.infoValue), isDescendantOfA(withId(R.id.tracksInfo))))
            .check(matches(withText("2")))
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

    @Test
    fun visitor_returns_to_catalog_with_system_back() {
        openAlbum()
        waitForDisplayed(withId(R.id.albumName))
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
        waitForDisplayed(withId(R.id.albumName))
        val requests = if (proxyEnabled) proxyControl("stats", post = false).getInt("detailRequests") else null
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
            if (requests != null) {
                assertEquals(requests, proxyControl("stats", post = false).getInt("detailRequests"))
            }
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
        waitForDisplayed(withId(R.id.albumName)).check(matches(withText("Poeta del pueblo")))

        scrollToMessage(R.string.album_detail_no_tracks)
        scrollToMessage(R.string.album_detail_no_comments)

        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.album_detail_title)))
    }

    @Test
    fun missing_album_shows_404_and_allows_return() {
        proxyControl("next/404")
        openAlbum()

        waitForDisplayed(withId(R.id.errorTitle)).check(matches(withText(R.string.album_detail_not_found)))
        onView(withId(R.id.detailList)).check(matches(not(isDisplayed())))
        onView(withId(R.id.retryButton)).check(matches(not(isDisplayed())))

        onView(withId(R.id.headerBack)).perform(click())
        waitForDisplayed(withText("Buscando América"))
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.album_list_title)))
    }

    @Test
    fun server_error_recovers_after_retry() {
        proxyControl("next/503")
        openAlbum()
        waitForDisplayed(withId(R.id.errorTitle)).check(matches(withText(R.string.album_detail_error)))
        onView(withId(R.id.detailList)).check(matches(not(isDisplayed())))

        onView(withId(R.id.retryButton)).perform(click())

        waitForDisplayed(withId(R.id.albumName)).check(matches(withText("Buscando América")))
        onView(withId(R.id.errorScroll)).check(matches(not(isDisplayed())))
    }

    @Test
    fun connection_failure_shows_network_message_and_retry_recovers() {
        proxyControl("next/disconnect")
        openAlbum()
        waitForDisplayed(withId(R.id.errorBody)).check(matches(withText(R.string.album_list_error_body)))

        onView(withId(R.id.retryButton)).perform(click())

        waitForDisplayed(withId(R.id.albumName)).check(matches(withText("Buscando América")))
        onView(withId(R.id.errorScroll)).check(matches(not(isDisplayed())))
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

    private fun proxyControl(
        action: String,
        post: Boolean = true,
    ): JSONObject {
        assumeTrue("Requires hu02E2eProxy=true and scripts/hu02_e2e_proxy.py", proxyEnabled)
        val connection = URL(BuildConfig.BASE_URL + "__hu02_e2e__/" + action).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = if (post) "POST" else "GET"
            connection.connectTimeout = CONTROL_TIMEOUT_MILLIS
            connection.readTimeout = CONTROL_TIMEOUT_MILLIS
            assertEquals(HttpURLConnection.HTTP_OK, connection.responseCode)
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
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
        const val CONTROL_TIMEOUT_MILLIS = 3_000
        const val SCREENSHOT_IDLE_MILLIS = 100L
        const val SCREENSHOT_TIMEOUT_MILLIS = 3_000L
    }
}
