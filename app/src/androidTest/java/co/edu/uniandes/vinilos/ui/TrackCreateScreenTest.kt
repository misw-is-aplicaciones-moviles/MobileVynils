package co.edu.uniandes.vinilos.ui

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.robots.AlbumListRobot
import co.edu.uniandes.vinilos.robots.TrackCreateRobot
import co.edu.uniandes.vinilos.testing.BackendFirewall
import co.edu.uniandes.vinilos.testing.createAlbumOnBackend
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import co.edu.uniandes.vinilos.testing.waitUntilGone
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * Hoja de nueva canción contra BackVynils local (10.0.2.2:3000); un método por caso E2E del inventario de docs/06.
 * Cada caso crea su álbum con POST /albums y un nombre único, así no depende de la semilla.
 */
@RunWith(AndroidJUnit4::class)
class TrackCreateScreenTest {
    private val firewall = BackendFirewall()
    private val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule val ruleChain: TestRule = RuleChain.outerRule(firewall).around(activityRule)

    private val catalog = AlbumListRobot()
    private val sheet = TrackCreateRobot()

    @Test
    fun createsTrackOnANewAlbum() {
        val album = openCreatedAlbum()
        assertEmptyAlbum()
        openSheet()

        sheet
            .assertSubtitle(album, number = 1)
            .typeName("Todos vuelven")
            .enterDigits(4, 1, 5)
            .assertDuration("04:15")
            .tapSave()

        waitForDisplayed(withText(R.string.snack_track_added))
        assertTrack(number = "01", name = "Todos vuelven", duration = "4:15")
        assertTrackCount("1")
        onView(withText(R.string.album_detail_no_tracks)).check(doesNotExist())
    }

    @Test
    fun rejectsDurationBoundariesWithoutSaving() {
        openCreatedAlbum()
        assertTrackCount("0")
        openSheet()

        sheet
            .typeName("Límite")
            .assertNameErrorHidden()
            .assertDurationErrorHidden()
            .tapSave()
            .assertDurationErrorShown()
            .enterDigits(1)
            .assertDuration("00:01")
            .assertDurationErrorHidden()
            .tapClear()
            .assertDuration("00:00")
            .assertDurationErrorShown()
            .enterDigits(5, 9, 5, 9)
            .assertDuration("59:59")
            .assertDurationErrorHidden()
            .tapClear()
            .enterDigits(5, 6, 0)
            .assertDuration("05:60")
            .assertDurationErrorShown()
            .tapClear()
            .enterDigits(6, 0, 0, 0)
            .assertDuration("60:00")
            .assertDurationErrorShown()
            .tapCancel()

        assertClosedWithoutCreating()
    }

    @Test
    fun retriesTrackSaveAfterTheNetworkReturns() {
        val track = "Reintento ${System.currentTimeMillis()}"
        openCreatedAlbum()
        openSheet()
        sheet.typeName(track).enterDigits(1, 3, 0).assertDuration("01:30")

        try {
            firewall.block()
            sheet
                .tapSave()
                .assertNetworkError()
                .assertName(track)
                .assertDuration("01:30")
                .assertOpen()
        } finally {
            firewall.restore()
        }

        sheet.tapSave()
        waitForDisplayed(withText(R.string.snack_track_added))
        assertTrack(number = "01", name = track, duration = "1:30")
        assertTrackCount("1")
    }

    @Test
    fun rejectsAMissingOrBlankTrackName() {
        openCreatedAlbum()
        openSheet()

        sheet
            .assertNameErrorHidden()
            .assertDurationErrorHidden()
            .enterDigits(3, 2, 0)
            .assertDuration("03:20")
            .tapSave()
            .assertNameErrorShown()
            .assertDurationErrorHidden()
            .assertOpen()
            .typeName("   ")
            .tapSave()
            .assertNameErrorShown()
            .assertOpen()
            .tapCancel()

        assertClosedWithoutCreating()
    }

    @Test
    fun cancelAndCloseDoNotCreateATrack() {
        openCreatedAlbum()
        openSheet()
        sheet.typeName("Canción descartada").enterDigits(1, 4, 5).tapCancel()
        assertClosedWithoutCreating("Canción descartada")

        openSheet()
        sheet.typeName("Canción descartada").enterDigits(1, 4, 5).tapClose()
        assertClosedWithoutCreating("Canción descartada")
    }

    @Test
    fun createdTrackStaysOnlyOnItsAlbum() {
        val stamp = System.currentTimeMillis()
        val albumA = "Tracks $stamp A"
        val albumB = "Tracks $stamp B"
        createAlbumOnBackend(albumA)
        createAlbumOnBackend(albumB)
        catalog.enterAs(R.string.role_collector)

        openAlbum(albumA)
        assertEmptyAlbum()
        openSheet()
        sheet
            .assertSubtitle(albumA, number = 1)
            .typeName("  Campesino  ")
            .enterDigits(2, 1, 0)
            .assertDuration("02:10")
            .tapSave()

        waitForDisplayed(withText(R.string.snack_track_added))
        assertSavedCampesino()
        leaveDetail()
        openAlbum(albumA)
        assertSavedCampesino()
        leaveDetail()
        openAlbum(albumB)
        assertEmptyAlbum()
        onView(allOf(withText("Campesino"), isDisplayed(), hasWindowFocus())).check(doesNotExist())
    }

    @Test
    fun keypadEntersFromTheRightAndClears() {
        openCreatedAlbum()
        openSheet()

        sheet
            .typeName("Teclado")
            .enterDigits(1, 2, 3, 4, 5)
            .assertDuration("23:45")
            .tapDelete()
            .assertDuration("02:34")
            .tapClear()
            .assertDuration("00:00")
            .tapSave()
            .assertDurationErrorShown()
            .assertOpen()
            .tapCancel()

        assertClosedWithoutCreating()
    }

    private fun openCreatedAlbum(): String {
        val name = "Tracks ${System.currentTimeMillis()}"
        createAlbumOnBackend(name)
        catalog.enterAs(R.string.role_collector)
        openAlbum(name)
        return name
    }

    private fun openAlbum(name: String) {
        catalog.scrollTo(name)
        onView(withId(R.id.albumList)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(hasDescendant(withText(name)), click()),
        )
        waitForDisplayed(allOf(withId(R.id.albumName), withText(name)))
    }

    private fun openSheet() {
        waitForDisplayed(withId(R.id.primaryAction)).perform(click())
        sheet.assertOpen()
    }

    private fun assertEmptyAlbum() {
        assertTrackCount("0")
        onView(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(withText(R.string.album_detail_no_tracks)),
        )
        onView(withText(R.string.album_detail_no_tracks)).check(matches(isDisplayed()))
    }

    private fun assertTrack(
        number: String,
        name: String,
        duration: String,
    ) {
        onView(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(name))),
        )
        onView(allOf(withId(R.id.trackNumber), withText(number))).check(matches(isDisplayed()))
        onView(withText(name)).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.trackDuration), withText(duration), isDisplayed())).check(matches(isDisplayed()))
    }

    private fun assertSavedCampesino() {
        assertTrack(number = "01", name = "Campesino", duration = "2:10")
        assertTrackCount("1")
        onView(allOf(withText(R.string.album_detail_no_tracks), isDisplayed(), hasWindowFocus()))
            .check(doesNotExist())
    }

    private fun assertTrackCount(count: String) {
        onView(
            allOf(
                withId(R.id.infoValue),
                isDescendantOfA(withId(R.id.tracksInfo)),
                withText(count),
                isDisplayed(),
                hasWindowFocus(),
            ),
        ).check(matches(isDisplayed()))
    }

    /** La pantalla anterior puede seguir en la jerarquía sin foco. Espresso si no la cuenta. */
    private fun hasWindowFocus(): Matcher<View> =
        object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("has window focus")
            }

            override fun matchesSafely(view: View): Boolean = view.hasWindowFocus()
        }

    private fun assertClosedWithoutCreating(absentName: String? = null) {
        waitUntilGone(withId(R.id.sheetTitle))
        onView(withText(R.string.snack_track_added)).check(doesNotExist())
        assertTrackCount("0")
        absentName?.let { onView(withText(it)).check(doesNotExist()) }
    }

    private fun leaveDetail() {
        onView(withId(R.id.headerBack)).perform(click())
        waitForDisplayed(withId(R.id.albumList))
    }
}
