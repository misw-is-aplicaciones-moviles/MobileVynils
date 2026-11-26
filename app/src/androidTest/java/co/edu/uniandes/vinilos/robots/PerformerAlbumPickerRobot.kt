package co.edu.uniandes.vinilos.robots

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.clickVisible
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Matchers.allOf

/** HU15 screen actions; assertions stay in the scenarios. */
class PerformerAlbumPickerRobot {
    fun open() {
        waitForDisplayed(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withId(R.id.addArtistAlbumButton))),
        )
        onView(allOf(withId(R.id.addArtistAlbumButton), isDisplayed(), isEnabled())).perform(clickVisible())
        waitForDisplayed(withId(R.id.searchField))
    }

    fun search(term: String) {
        onView(withId(R.id.searchField)).perform(replaceText(term), closeSoftKeyboard())
    }

    fun select(name: String) {
        search(name)
        waitForDisplayed(allOf(withId(R.id.albumTitle), withText(name)))
        onView(withId(R.id.optionList)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(hasDescendant(withText(name)), clickVisible()),
        )
    }
}
