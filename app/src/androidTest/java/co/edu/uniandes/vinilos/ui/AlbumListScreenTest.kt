package co.edu.uniandes.vinilos.ui

import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.robots.AlbumListRobot
import co.edu.uniandes.vinilos.testing.createAlbumOnBackend
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Catálogo contra BackVynils local (10.0.2.2:3000 con la semilla); un método por caso del inventario de docs/06.
 * Los álbumes que crean las pruebas empiezan por letras posteriores a "Poeta", así que la semilla conserva sus filas.
 */
@RunWith(AndroidJUnit4::class)
class AlbumListScreenTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val catalog = AlbumListRobot()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun visitor_sees_the_seed_albums_in_alphabetical_order_without_the_create_button() {
        catalog
            .enterAs(R.string.role_visitor)
            .assertRow(
                position = 0,
                number = "01",
                title = "A Day at the Races",
                artist = "Queen",
                meta = "Rock · 1976",
            ).assertTitles("A Day at the Races", "A Night at the Opera", "Buscando América", "Poeta del pueblo")
            .assertCreateButtonHidden()
    }

    @Test
    fun collector_sees_the_create_button_and_it_opens_the_new_album_form() {
        catalog
            .enterAs(R.string.role_collector)
            .assertTitles("A Day at the Races", "A Night at the Opera", "Buscando América", "Poeta del pueblo")
            .assertCreateButtonShown()
            .tapCreate()

        waitForDisplayed(withText(R.string.create_album_all_required))
    }

    @Test
    fun album_without_performers_shows_no_artist() {
        val name = "Sin artista ${System.currentTimeMillis()}"
        createAlbumOnBackend(name)

        catalog
            .enterAs(R.string.role_visitor)
            .assertArtistOf(name, context.getString(R.string.album_no_artist))
    }

    @Test
    fun pull_to_refresh_shows_an_album_added_on_the_backend() {
        val name = "Refresco ${System.currentTimeMillis()}"
        catalog
            .enterAs(R.string.role_visitor)
            .assertTitles("A Day at the Races")
        createAlbumOnBackend(name)

        catalog
            .pullToRefresh()
            .scrollTo(name)
            .assertShown(name)
    }

    @Test
    fun recreating_the_screen_keeps_the_catalog_and_its_position() {
        catalog
            .enterAs(R.string.role_visitor)
            .scrollTo("Poeta del pueblo")

        activityRule.scenario.recreate()

        waitForDisplayed(withText("Poeta del pueblo"))
        catalog.assertCreateButtonHidden()
    }
}
