package co.edu.uniandes.vinilos.ui

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
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
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.robots.AlbumListRobot
import co.edu.uniandes.vinilos.robots.PerformerListRobot
import co.edu.uniandes.vinilos.testing.BackendPerformers
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/** Initial HU04 UI coverage using only real app entries and owned backend fixtures. */
@RunWith(AndroidJUnit4::class)
class PerformerDetailScreenTest {
    private val data = BackendPerformers()
    private val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule val ruleChain: TestRule = RuleChain.outerRule(data).around(activityRule)
    private val artists = PerformerListRobot()
    private val name = "HU04 ${System.nanoTime()}"
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun musician_album_return_recreation_and_artists_tab_keep_the_expected_content() {
        data.createMusician(name, albumCount = 1)
        artists.enterAs(R.string.role_visitor).tapRow(name)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(name)))
        onView(withId(R.id.performerDescription)).check(matches(withText("Artista creado por una prueba E2E.")))
        onView(withId(R.id.performerDate)).check(matches(withText(containsString("1947"))))
        onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())))
        scrollToText(context.getString(R.string.artist_detail_albums, 1))
        scrollToText("$name álbum 1")
        activityRule.scenario.onActivity { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.detailList)
            list.scrollToPosition(checkNotNull(list.adapter).itemCount - 1)
        }
        onView(withId(R.id.addPrizeButton)).check(doesNotExist())
        onView(withId(R.id.albumTitle)).perform(click())
        waitForDisplayed(allOf(withId(R.id.albumName), withText("$name álbum 1")))
        onView(withId(R.id.headerBack)).perform(click())
        waitForDisplayed(allOf(withId(R.id.albumTitle), withText("$name álbum 1")))

        activityRule.scenario.recreate()

        waitForDisplayed(allOf(withId(R.id.albumTitle), withText("$name álbum 1")))
        onView(withId(R.id.performerListFragment)).check(matches(isSelected())).perform(click())
        artists.scrollTo(name)
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.artist_list_title)))
    }

    @Test
    fun band_opens_its_member_and_returns_as_collector() {
        val memberName = "$name integrante"
        val member = data.createMusician(memberName)
        data.createBand(name, albumCount = 1, members = listOf(member))
        artists.enterAs(R.string.role_collector).tapRow(name)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(name)))
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.artist_band)))
        onView(withId(R.id.performerDate)).check(matches(withText(containsString("1970"))))
        scrollToText(context.getString(R.string.artist_detail_members, 1))
        onView(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(withId(R.id.performerMembers)),
        )
        waitForDisplayed(withId(R.id.performerMembers)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(memberName))),
        )
        waitForDisplayed(allOf(withId(R.id.memberName), withText(memberName)))
        onView(withId(R.id.memberName)).perform(click())
        waitForDisplayed(allOf(withId(R.id.performerName), withText(memberName)))
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.artist_musician)))
        pressBack()
        waitForDisplayed(allOf(withId(R.id.headerTitle), withText(R.string.artist_band)))
        scrollToText("$name álbum 1")
        onView(withId(R.id.roleChip)).check(matches(withText(R.string.role_collector)))
        onView(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withId(R.id.addPrizeButton))),
        )
        onView(withId(R.id.addPrizeButton)).check(matches(allOf(isDisplayed(), not(isEnabled()))))
        onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())))
    }

    @Test
    fun empty_sections_keep_the_musician_and_band_visible() {
        data.createMusician(name)
        val bandName = "$name banda"
        data.createBand(bandName)
        artists.enterAs(R.string.role_visitor).tapRow(name)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(name)))
        scrollToText(context.getString(R.string.artist_detail_no_albums))
        onView(withId(R.id.headerBack)).perform(click())
        artists.tapRow(bandName)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(bandName)))
        scrollToText(context.getString(R.string.artist_detail_no_members))
        scrollToText(context.getString(R.string.artist_detail_no_albums))
    }

    @Test
    fun album_artist_opens_typed_detail_and_back_returns_to_the_same_album() {
        val artistId = data.createMusician(name, albumCount = 1)
        artists.chooseRole(R.string.role_visitor)
        AlbumListRobot().scrollTo("$name álbum 1")
        onView(withId(R.id.albumList)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                hasDescendant(withText("$name álbum 1")),
                click(),
            ),
        )
        waitForDisplayed(allOf(withId(R.id.albumName), withText("$name álbum 1")))
        onView(withId(R.id.artistLink)).perform(click())
        waitForDisplayed(allOf(withId(R.id.performerName), withText(name)))
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.artist_musician)))
        onView(withId(R.id.performerListFragment)).check(matches(isSelected()))
        pressBack()
        waitForDisplayed(allOf(withId(R.id.albumName), withText("$name álbum 1")))
        onView(withId(R.id.albumListFragment)).check(matches(isSelected()))
        onView(withId(R.id.headerBack)).perform(click())
        data.deleteMusician(artistId)
        AlbumListRobot().scrollTo("$name álbum 1")
        onView(withId(R.id.albumList)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                hasDescendant(withText("$name álbum 1")),
                click(),
            ),
        )
        waitForDisplayed(allOf(withId(R.id.albumArtist), withText(R.string.album_no_artist)))
        onView(withId(R.id.artistLink)).check(matches(not(isClickable())))
    }

    @Test
    fun removed_musician_shows_404_allows_retry_and_returns_to_artists() {
        val id = data.createMusician(name)
        artists.enterAs(R.string.role_visitor).scrollTo(name)
        data.deleteMusician(id)
        artists.tapRow(name)
        waitForDisplayed(withText(R.string.artist_detail_not_found))
        onView(withId(R.id.retryButton)).perform(click())
        waitForDisplayed(withText(R.string.artist_detail_not_found))
        onView(withId(R.id.headerBack)).perform(click())
        waitForDisplayed(allOf(withId(R.id.headerTitle), withText(R.string.artist_list_title)))
        onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())))
    }

    private fun scrollToText(text: String) {
        onView(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                anyOf(withText(text), hasDescendant(withText(text))),
            ),
        )
        waitForDisplayed(allOf(withText(text), isDescendantOfA(withId(R.id.detailList))))
    }
}
