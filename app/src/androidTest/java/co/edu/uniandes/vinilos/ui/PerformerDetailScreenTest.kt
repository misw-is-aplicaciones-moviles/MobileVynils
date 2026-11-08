package co.edu.uniandes.vinilos.ui

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isClickable
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
import co.edu.uniandes.vinilos.robots.PerformerDetailRobot
import co.edu.uniandes.vinilos.robots.PerformerListRobot
import co.edu.uniandes.vinilos.testing.BackendPerformers
import co.edu.uniandes.vinilos.testing.clickVisible
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/** HU04: recorridos reales con datos propios, sin depender de posiciones o conteos de la semilla. */
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
        data.createMusician(name, albumCount = 8)
        val albumName = "$name álbum 8"
        artists.enterAs(R.string.role_visitor).tapRow(name)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(name)))
        onView(withId(R.id.performerDescription)).check(matches(withText("Artista creado por una prueba E2E.")))
        onView(withId(R.id.performerDate)).check(matches(withText(containsString("1947"))))
        onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())))
        scrollToText(context.getString(R.string.artist_detail_albums, 8))
        scrollToText(context.getString(R.string.prize_detail_count, 0))
        onView(withId(R.id.addPrizeButton)).check(matches(not(isDisplayed())))
        scrollToText(albumName)
        onView(allOf(withId(R.id.albumTitle), withText(albumName))).perform(click())
        scrollToText(albumName)
        onView(withId(R.id.headerBack)).perform(click())
        waitForDisplayed(allOf(withId(R.id.albumTitle), withText(albumName)))
        var firstVisible = RecyclerView.NO_POSITION
        activityRule.scenario.onActivity { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.detailList)
            firstVisible = (list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        }

        activityRule.scenario.recreate()

        waitForDisplayed(allOf(withId(R.id.albumTitle), withText(albumName)))
        activityRule.scenario.onActivity { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.detailList)
            assertEquals(firstVisible, (list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
        }
        onView(withId(R.id.roleChip)).check(matches(withText(R.string.role_visitor)))
        onView(withId(R.id.performerListFragment)).check(matches(isSelected())).perform(click())
        artists.scrollTo(name)
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.artist_list_title)))
    }

    @Test
    fun band_opens_its_member_and_returns_as_collector() {
        val memberNames = (1..6).map { "$name integrante $it" }
        val members = memberNames.map { data.createMusician(it) }
        val memberName = memberNames.last()
        data.createBand(name, albumCount = 1, members = members)
        artists.enterAs(R.string.role_collector).tapRow(name)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(name)))
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.artist_band)))
        onView(withId(R.id.performerDate)).check(matches(withText(containsString("1970"))))
        scrollToText(context.getString(R.string.artist_detail_members, members.size))
        onView(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(withId(R.id.performerMembers)),
        )
        waitForDisplayed(withId(R.id.performerMembers)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(memberName))),
        )
        waitForDisplayed(allOf(withId(R.id.memberName), withText(memberName)))
        onView(allOf(withId(R.id.memberName), withText(memberName))).perform(click())
        waitForDisplayed(allOf(withId(R.id.performerName), withText(memberName)))
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.artist_musician)))
        pressBack()
        waitForDisplayed(allOf(withId(R.id.headerTitle), withText(R.string.artist_band)))
        scrollToText("$name álbum 1")
        onView(withId(R.id.roleChip)).check(matches(withText(R.string.role_collector)))
        onView(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withId(R.id.addPrizeButton))),
        )
        onView(withId(R.id.addPrizeButton)).check(matches(allOf(isDisplayed(), isEnabled()))).perform(click())
        waitForDisplayed(withText(R.string.prize_picker_title))
        onView(withId(R.id.bottomNav)).check(matches(not(isDisplayed())))
        onView(withId(R.id.prizeSearch)).perform(replaceText(name), closeSoftKeyboard())
        waitForDisplayed(withText(R.string.prize_no_results))
        onView(withId(R.id.headerClose)).perform(click())
        waitForDisplayed(allOf(withId(R.id.headerTitle), withText(R.string.artist_band)))
        scrollToText(context.getString(R.string.prize_detail_count, 0))
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
        onView(withId(R.id.headerBack)).perform(clickVisible())
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
        scrollToText("$name álbum 1")
        scrollToText(name)
        onView(withId(R.id.artistLink)).perform(click())
        waitForDisplayed(allOf(withId(R.id.performerName), withText(name)))
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.artist_musician)))
        onView(withId(R.id.performerListFragment)).check(matches(isSelected()))
        pressBack()
        scrollToText("$name álbum 1")
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
        scrollToText(context.getString(R.string.album_no_artist))
        onView(withId(R.id.artistLink)).check(matches(not(isClickable())))
    }

    @Test
    fun role_selector_clears_detail_and_reopening_preserves_the_new_role() {
        data.createMusician(name)
        artists.enterAs(R.string.role_collector).tapRow(name)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(name)))
        scrollToText(context.getString(R.string.prize_detail_count, 0))
        // En API 21 conviven un instante el botón deshabilitado de la carga y el habilitado.
        onView(allOf(withId(R.id.addPrizeButton), isEnabled(), isDisplayed())).perform(clickVisible())
        waitForDisplayed(withText(R.string.prize_picker_title))
        pressBack()
        waitForDisplayed(allOf(withId(R.id.headerTitle), withText(R.string.artist_musician)))
        onView(withId(R.id.roleChip)).perform(click())
        waitForDisplayed(withText(R.string.role_question))
        onView(withId(R.id.bottomNav)).check(matches(not(isDisplayed())))

        artists.enterAs(R.string.role_visitor).tapRow(name)
        waitForDisplayed(allOf(withId(R.id.performerName), withText(name)))
        onView(withId(R.id.roleChip)).check(matches(withText(R.string.role_visitor)))
        scrollToText(context.getString(R.string.prize_detail_count, 0))
        onView(allOf(withId(R.id.addPrizeButton), isDisplayed())).check(doesNotExist())
        pressBack()
        waitForDisplayed(allOf(withId(R.id.headerTitle), withText(R.string.artist_list_title)))
        onView(withId(R.id.roleChip)).check(matches(withText(R.string.role_visitor)))
    }

    @Test
    fun removed_musician_shows_404_allows_retry_and_returns_to_artists() {
        val id = data.createMusician(name)
        artists.enterAs(R.string.role_visitor).scrollTo(name)
        data.deleteMusician(id)
        artists.tapRow(name)
        waitForDisplayed(withText(R.string.artist_detail_not_found))
        onView(withId(R.id.retryButton)).perform(clickVisible())
        waitForDisplayed(withText(R.string.artist_detail_not_found))
        onView(withId(R.id.headerBack)).perform(click())
        waitForDisplayed(allOf(withId(R.id.headerTitle), withText(R.string.artist_list_title)))
        onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())))
    }

    private fun scrollToText(text: String) = PerformerDetailRobot().scrollToText(text)
}
