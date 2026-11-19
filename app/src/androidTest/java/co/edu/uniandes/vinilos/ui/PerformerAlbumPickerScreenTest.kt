package co.edu.uniandes.vinilos.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.BuildConfig
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.robots.AlbumListRobot
import co.edu.uniandes.vinilos.robots.BandMemberPickerRobot
import co.edu.uniandes.vinilos.robots.CollectorAlbumPickerRobot
import co.edu.uniandes.vinilos.robots.CollectorDetailRobot
import co.edu.uniandes.vinilos.robots.CollectorListRobot
import co.edu.uniandes.vinilos.robots.PerformerAlbumPickerRobot
import co.edu.uniandes.vinilos.robots.PerformerDetailRobot
import co.edu.uniandes.vinilos.robots.PerformerListRobot
import co.edu.uniandes.vinilos.testing.BackendPerformers
import co.edu.uniandes.vinilos.testing.clickVisible
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import co.edu.uniandes.vinilos.testing.waitUntilGone
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.net.URI

/** Real BackVynils E2E: unique owned fixtures, no seed associations and no association DELETE endpoints. */
@RunWith(AndroidJUnit4::class)
class PerformerAlbumPickerScreenTest {
    private val data = BackendPerformers()
    private val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule val rules: TestRule = RuleChain.outerRule(data).around(activityRule)
    private val artists = PerformerListRobot()
    private val picker = PerformerAlbumPickerRobot()
    private val name = "HU15 ${System.nanoTime()}"
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun requireLocalBackend() {
        check(URI(BuildConfig.BASE_URL).host in setOf("127.0.0.1", "localhost", "10.0.2.2")) {
            "HU15 E2E requires a local test backend"
        }
    }

    @Test
    fun musician_association_returns_to_updated_detail_with_one_album_link() {
        val id = data.createMusician(name)
        val albumName = "$name libre"
        val albumId = data.createAlbum(albumName)
        artists.chooseRole(R.string.role_collector)
        AlbumListRobot().assertArtistOf(albumName, context.getString(R.string.album_no_artist))
        artists.openArtistsTab().waitForList().tapRow(name)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(name)))
        picker.open()
        onView(withId(R.id.bottomNav)).check(matches(not(isDisplayed())))
        onView(withId(R.id.confirmButton)).check(matches(not(isEnabled())))
        picker.select(albumName)
        waitForDisplayed(allOf(withId(R.id.confirmButton), isEnabled())).perform(clickVisible())
        assertReturned(albumName, 1)
        assertEquals(setOf(albumId), ids(data.artist(id, PerformerType.MUSICIAN), "albums"))
        assertEquals(setOf(id), ids(data.album(albumId), "performers"))
        pressBack()
        artists.waitForList().waitForRow(name, overline = "Músico · 1947", summary = "1 álbum")

        // Read-only navigation through a seed collector: a fresh HU11 selector must not reuse the old summary.
        CollectorListRobot().openCollectorsTab().waitForList().tapRow("Manolo Bellon")
        CollectorDetailRobot().waitUntilReady("Manolo Bellon").tapAddAlbum()
        CollectorAlbumPickerRobot().waitUntilOpen("Manolo Bellon").search(albumName)
        waitForDisplayed(allOf(withId(R.id.albumSubtitle), withText(name)))
        onView(withId(R.id.headerClose)).perform(clickVisible())
        waitForDisplayed(allOf(withId(R.id.headerTitle), withText(R.string.collector_detail_title)))
        onView(withId(R.id.albumListFragment)).perform(click())
        waitForDisplayed(withId(R.id.albumList))
        AlbumListRobot().assertArtistOf(albumName, name)
    }

    @Test
    fun band_association_preserves_members_and_existing_albums() {
        val members = listOf(data.createMusician("$name integrante"))
        val id = data.createBand(name, albumCount = 1, members = members)
        val before = data.artist(id, PerformerType.BAND)
        val albumName = "$name nuevo"
        val albumId = data.createAlbum(albumName)
        openArtist()
        picker.open()
        picker.select(albumName)
        waitForDisplayed(allOf(withId(R.id.confirmButton), isEnabled())).perform(clickVisible())
        assertReturned(albumName, 2)
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.artist_band)))
        val after = data.artist(id, PerformerType.BAND)
        assertEquals(ids(before, "albums") + albumId, ids(after, "albums"))
        assertEquals(members.toSet(), ids(after, "musicians"))
        assertEquals(ids(before, "performerPrizes"), ids(after, "performerPrizes"))
        assertEquals(setOf(id), ids(data.album(albumId), "performers"))
    }

    @Test
    fun own_and_foreign_albums_are_blocked_and_do_not_change_relations() {
        val id = data.createMusician(name, albumCount = 1)
        val other = data.createBand("$name otra banda", albumCount = 1)
        val ownName = "$name álbum 1"
        val otherName = "$name otra banda álbum 1"
        val before = data.artist(other, PerformerType.BAND)
        openArtist()
        picker.open()
        picker.select(ownName)
        onView(withId(R.id.addedChip)).check(matches(isDisplayed()))
        onView(withId(R.id.confirmButton)).check(matches(not(isEnabled())))
        picker.select(otherName)
        onView(allOf(withId(R.id.albumRow), hasDescendant(withText(otherName))))
            .check(matches(not(isClickable())))
        onView(withId(R.id.radioIcon)).check(matches(not(isDisplayed())))
        onView(withId(R.id.confirmButton)).check(matches(not(isEnabled())))
        assertEquals(1, ids(data.artist(id, PerformerType.MUSICIAN), "albums").size)
        assertEquals(ids(before, "albums"), ids(data.artist(other, PerformerType.BAND), "albums"))
    }

    @Test
    fun search_selection_and_cancel_survive_recreation_without_writing() {
        val id = data.createMusician(name)
        val albumName = "$name libre"
        val albumId = data.createAlbum(albumName)
        openArtist()
        picker.open()
        picker.search("zzzz-no-match")
        waitForDisplayed(withText(R.string.collector_album_picker_no_results))
        picker.select(albumName)
        activityRule.scenario.recreate()
        waitForDisplayed(allOf(withId(R.id.searchField), withText(albumName)))
        waitForDisplayed(allOf(withId(R.id.confirmButton), isEnabled()))
        onView(allOf(withId(R.id.albumRow), hasDescendant(withText(albumName)))).check(matches(isSelected()))
        onView(withId(R.id.headerClose)).perform(clickVisible())
        waitForDisplayed(allOf(withId(R.id.headerTitle), withText(R.string.artist_musician)))
        assertEquals(emptySet<Int>(), ids(data.artist(id, PerformerType.MUSICIAN), "albums"))
        assertEquals(emptySet<Int>(), ids(data.album(albumId), "performers"))
    }

    @Test
    fun visitor_cannot_open_the_album_picker() {
        val id = data.createMusician(name)
        artists.enterAs(R.string.role_visitor).tapRow(name)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(name)))
        PerformerDetailRobot().scrollToText(context.getString(R.string.artist_detail_albums, 0))
        onView(withId(R.id.addArtistAlbumButton)).check(matches(not(isDisplayed())))
        assertEquals(emptySet<Int>(), ids(data.artist(id, PerformerType.MUSICIAN), "albums"))
    }

    @Test
    fun availability_is_rechecked_before_writing_and_another_artist_is_preserved() {
        val id = data.createBand(name)
        val other = data.createMusician("$name otro")
        val albumName = "$name libre"
        val albumId = data.createAlbum(albumName)
        openArtist()
        picker.open()
        picker.select(albumName)
        data.associateAlbum(other, PerformerType.MUSICIAN, albumId)
        onView(withId(R.id.confirmButton)).perform(clickVisible())
        waitForDisplayed(withText(R.string.artist_album_unavailable))
        onView(withId(R.id.confirmButton)).check(matches(not(isEnabled())))
        assertEquals(emptySet<Int>(), ids(data.artist(id, PerformerType.BAND), "albums"))
        assertEquals(setOf(other), ids(data.album(albumId), "performers"))
        pressBack()
        waitForDisplayed(allOf(withId(R.id.headerTitle), withText(R.string.artist_band)))
    }

    @Test
    fun removed_artist_returns_failed_save_without_false_success() {
        val id = data.createMusician(name)
        val albumName = "$name libre"
        val albumId = data.createAlbum(albumName)
        openArtist()
        picker.open()
        picker.select(albumName)
        data.deleteMusician(id)
        onView(withId(R.id.confirmButton)).perform(clickVisible())
        waitForDisplayed(withText(R.string.artist_album_not_saved))
        onView(withId(R.id.confirmButton)).check(matches(isEnabled()))
        onView(withId(R.id.searchField)).check(matches(isDisplayed()))
        assertEquals(emptySet<Int>(), ids(data.album(albumId), "performers"))
    }

    @Test
    fun load_failure_can_be_retried_and_closed_without_a_write() {
        val id = data.createMusician(name)
        openArtist()
        data.deleteMusician(id)
        picker.open()
        waitForDisplayed(withText(R.string.artist_album_picker_error))
        onView(withId(R.id.confirmButton)).check(matches(not(isEnabled())))
        onView(withId(R.id.retryButton)).perform(clickVisible())
        waitForDisplayed(withText(R.string.artist_album_picker_error))
        onView(withId(R.id.headerClose)).perform(clickVisible())
        waitForDisplayed(allOf(withId(R.id.headerTitle), withText(R.string.artist_musician)))
    }

    @Test
    fun band_keeps_new_member_and_album_when_both_pickers_are_used() {
        val addedName = "$name nuevo integrante"
        val added = data.createMusician(addedName)
        val id = data.createBand(name, albumCount = 1)
        val albumName = "$name nuevo álbum"
        val albumId = data.createAlbum(albumName)
        val before = ids(data.artist(id, PerformerType.BAND), "albums")
        openArtist()
        BandMemberPickerRobot().openFromBandDetail().select(addedName).tapAdd()
        waitForDisplayed(withText(R.string.band_member_added_message))
        BandMemberPickerRobot().assertMemberInBand(addedName, count = 1)
        waitUntilGone(withText(R.string.band_member_added_message))
        picker.open()
        picker.select(albumName)
        waitForDisplayed(allOf(withId(R.id.confirmButton), isEnabled())).perform(clickVisible())
        assertReturned(albumName, 2)
        val after = data.artist(id, PerformerType.BAND)
        assertEquals(before + albumId, ids(after, "albums"))
        assertEquals(setOf(added), ids(after, "musicians"))
        pressBack()
        waitForDisplayed(allOf(withId(R.id.headerTitle), withText(R.string.artist_list_title)))
        artists.waitForRow(name, overline = "Banda · desde 1970", summary = "2 álbumes · 1 integrante")
        artists.waitForRow(addedName, overline = "Músico · 1947", summary = "Integrante de $name")
        artists.tapRow(name)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(name)))
        PerformerDetailRobot().scrollToText(context.getString(R.string.artist_detail_albums, 2))
        BandMemberPickerRobot().assertMemberInBand(addedName, count = 1)
    }

    private fun openArtist() {
        artists.enterAs(R.string.role_collector).tapRow(name)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(name)))
    }

    private fun assertReturned(
        albumName: String,
        count: Int,
    ) {
        waitForDisplayed(
            allOf(
                withId(R.id.albumTitle),
                withText(albumName),
                isDescendantOfA(withId(R.id.detailList)),
            ),
        )
        onView(allOf(withId(R.id.albumTitle), withText(albumName))).check(
            matches(isDescendantOfA(allOf(isSelected(), isDescendantOfA(withId(R.id.detailList))))),
        )
        PerformerDetailRobot().scrollToText(context.getString(R.string.artist_detail_albums, count))
        onView(withId(R.id.performerListFragment)).check(matches(isSelected()))
    }

    private fun ids(
        value: JSONObject,
        key: String,
    ): Set<Int> {
        val array = value.getJSONArray(key)
        return (0 until array.length()).mapTo(mutableSetOf()) { array.getJSONObject(it).getInt("id") }
    }
}
