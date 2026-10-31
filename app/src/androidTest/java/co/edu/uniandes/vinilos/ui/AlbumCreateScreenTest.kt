package co.edu.uniandes.vinilos.ui

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.robots.AlbumCreateRobot
import co.edu.uniandes.vinilos.robots.MainRobot
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Formulario de nuevo álbum contra BackVynils local (10.0.2.2:3000); un método por caso del inventario de docs/06.
 * La creación válida guarda un álbum real con un nombre único por ejecución para poder repetirse.
 */
@RunWith(AndroidJUnit4::class)
class AlbumCreateScreenTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val form = AlbumCreateRobot()

    @Test
    fun createsAlbumWithValidData() {
        val name = "The Game ${System.currentTimeMillis()}"
        openFormAsCollector()

        form
            .typeName(name)
            .typeCoverUrl("https://example.com/the-game.jpg")
            .pickReleaseDate()
            .typeDescription("Noveno álbum de Queen.")
            .selectGenre("Rock")
            .selectRecordLabel("EMI")
            .tapCreate()

        waitForDisplayed(withText(R.string.snack_album_created))
        MainRobot().assertCatalogShown()
        onView(withId(R.id.albumList))
            .perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(name))))
        onView(withText(name)).check(matches(isDisplayed()))
    }

    @Test
    fun showsRequiredErrorsWhenEmpty() {
        openFormAsCollector()

        form
            .tapCreate()
            .assertFieldError(R.id.nameLayout, R.string.error_required)
            .assertFieldError(R.id.coverLayout, R.string.error_required)
            .assertFieldError(R.id.dateLayout, R.string.error_required)
            .assertFieldError(R.id.descriptionLayout, R.string.error_required)
            .assertChoiceError(R.id.genreError, R.string.error_select_genre)
            .assertChoiceError(R.id.recordLabelError, R.string.error_select_label)
            .assertFormShown()
    }

    @Test
    fun showsErrorsForBlankNameAndMissingDate() {
        openFormAsCollector()

        form
            .typeName("   ")
            .typeCoverUrl("https://example.com/the-game.jpg")
            .typeDescription("Noveno álbum de Queen.")
            .selectGenre("Rock")
            .selectRecordLabel("EMI")
            .tapCreate()
            .assertFieldError(R.id.nameLayout, R.string.error_required)
            .assertFieldError(R.id.dateLayout, R.string.error_required)
            .assertNoFieldError(R.id.coverLayout)
            .assertNoFieldError(R.id.descriptionLayout)
            .assertNoChoiceError(R.id.genreError)
            .assertNoChoiceError(R.id.recordLabelError)
            .assertText(R.id.coverInput, "https://example.com/the-game.jpg")
            .assertText(R.id.descriptionInput, "Noveno álbum de Queen.")
    }

    @Test
    fun clearsFieldErrorWhenFieldIsFilled() {
        openFormAsCollector()

        form
            .tapCreate()
            .typeName("The Game")
            .selectGenre("Rock")
            .assertNoFieldError(R.id.nameLayout)
            .assertNoChoiceError(R.id.genreError)
            .assertFieldError(R.id.coverLayout, R.string.error_required)
            .assertFieldError(R.id.dateLayout, R.string.error_required)
            .assertFieldError(R.id.descriptionLayout, R.string.error_required)
            .assertChoiceError(R.id.recordLabelError, R.string.error_select_label)
    }

    @Test
    fun hidesCreateActionForVisitor() {
        onView(withText(R.string.role_visitor)).perform(click())

        waitForDisplayed(withId(R.id.albumList))
        onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())))
    }

    @Test
    fun cancelsWithoutCreating() {
        val name = "Cancelado ${System.currentTimeMillis()}"
        openFormAsCollector()

        form.typeName(name).tapCancel()
        assertBackInCatalogWithout(name)

        onView(withId(R.id.primaryAction)).perform(click())
        form.assertFormShown().typeName(name).tapClose()
        assertBackInCatalogWithout(name)
    }

    private fun openFormAsCollector() {
        onView(withText(R.string.role_collector)).perform(click())
        waitForDisplayed(withId(R.id.albumList))
        onView(withId(R.id.primaryAction)).perform(click())
        form.assertFormShown()
    }

    private fun assertBackInCatalogWithout(name: String) {
        waitForDisplayed(withId(R.id.albumList))
        MainRobot().assertCatalogShown()
        onView(withText(R.string.snack_album_created)).check(doesNotExist())
        onView(withText(name)).check(doesNotExist())
    }
}
