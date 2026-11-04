package co.edu.uniandes.vinilos.ui

import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.robots.PerformerListRobot
import co.edu.uniandes.vinilos.testing.BackendFirewall
import co.edu.uniandes.vinilos.testing.BackendPerformers
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * Lista de artistas contra BackVynils local (10.0.2.2:3000 con la semilla); un método por caso E2E del inventario de
 * docs/06. Cada caso crea sus propios músicos, bandas y álbumes con nombres únicos y los borra al terminar: la lista
 * puede tener además a Rubén Blades Bellido de Luna y a Queen de la semilla, y a cualquier otro artista.
 */
@RunWith(AndroidJUnit4::class)
class PerformerListScreenTest {
    private val data = BackendPerformers()
    private val firewall = BackendFirewall()
    private val activityRule = ActivityScenarioRule(MainActivity::class.java)

    // La limpieza de los datos se ejecuta al final: el corte de red ya se restauró y la actividad ya se cerró.
    @get:Rule val ruleChain: TestRule = RuleChain.outerRule(data).around(firewall).around(activityRule)

    private val artists = PerformerListRobot()
    private val unique = System.currentTimeMillis()

    @Test
    fun visitor_sees_musicians_and_bands_by_name_without_the_create_button() {
        val seed = createCast()
        artists.enterAs(R.string.role_visitor)

        artists
            .assertRow(seed.band, overline = "Banda · desde 1991", summary = "2 álbumes · 1 integrante")
            .assertRow(seed.member, overline = "Músico · 1947", summary = "Integrante de ${seed.band}")
            .assertRow(seed.solo, overline = "Músico · 1960", summary = "1 álbum")
            // Las filas de la semilla: sus cuentas dependen de otros datos, pero no su tipo ni su año.
            .assertOverline("Rubén Blades Bellido de Luna", overline = "Músico · 1948")
            .assertOverline("Queen", overline = "Banda · desde 1970")
            .assertSelected(R.id.filterAll)
            .assertBothTypes()
            .assertAlphabeticalOrder()
            .assertCreateButtonHidden()
    }

    @Test
    fun collector_sees_the_same_list_and_no_create_button() {
        val seed = createCast()
        artists.enterAs(R.string.role_collector)

        artists
            .assertRow(seed.band, overline = "Banda · desde 1991", summary = "2 álbumes · 1 integrante")
            .assertRow(seed.member, overline = "Músico · 1947", summary = "Integrante de ${seed.band}")
            .assertOverline("Rubén Blades Bellido de Luna", overline = "Músico · 1948")
            .assertAlphabeticalOrder()
            .assertCreateButtonHidden()
    }

    @Test
    fun third_line_shows_counts_for_bands_the_band_for_members_and_albums_for_the_rest() {
        val member = data.createMusician("E2E Integrante $unique")
        val second = data.createMusician("E2E Segundo $unique")
        data.createBand("E2E Trío $unique", albumCount = 2, members = listOf(member, second))
        data.createBand("E2E Dúo $unique", albumCount = 1, members = listOf(data.createMusician("E2E Voz $unique")))
        data.createMusician("E2E Solista $unique", albumCount = 2)
        artists.enterAs(R.string.role_visitor)

        artists
            .assertRow("E2E Trío $unique", overline = "Banda · desde 1970", summary = "2 álbumes · 2 integrantes")
            .assertRow("E2E Dúo $unique", overline = "Banda · desde 1970", summary = "1 álbum · 1 integrante")
            .assertRow("E2E Integrante $unique", overline = "Músico · 1947", summary = "Integrante de E2E Trío $unique")
            .assertRow("E2E Segundo $unique", overline = "Músico · 1947", summary = "Integrante de E2E Trío $unique")
            .assertRow("E2E Solista $unique", overline = "Músico · 1947", summary = "2 álbumes")
            .assertRow("E2E Voz $unique", overline = "Músico · 1947", summary = "Integrante de E2E Dúo $unique")
    }

    @Test
    fun musicians_filter_shows_only_musicians() {
        val seed = createCast()
        artists.enterAs(R.string.role_visitor).scrollTo(seed.band)

        artists
            .select(R.id.filterMusicians)
            .assertSelected(R.id.filterMusicians)
            .assertListed(seed.member, listed = true)
            .assertListed(seed.solo, listed = true)
            .assertListed(seed.band, listed = false)
            .assertListed("Queen", listed = false)
            .assertOnlyType(PerformerType.MUSICIAN)
            .assertAlphabeticalOrder()
    }

    @Test
    fun bands_filter_shows_only_bands() {
        val seed = createCast()
        artists.enterAs(R.string.role_visitor).scrollTo(seed.band)

        artists
            .select(R.id.filterBands)
            .assertSelected(R.id.filterBands)
            .assertListed(seed.band, listed = true)
            .assertListed("Queen", listed = true)
            .assertListed(seed.member, listed = false)
            .assertListed("Rubén Blades Bellido de Luna", listed = false)
            .assertOnlyType(PerformerType.BAND)
            .assertAlphabeticalOrder()
    }

    @Test
    fun todos_filter_brings_back_musicians_and_bands() {
        val seed = createCast()
        artists.enterAs(R.string.role_visitor).scrollTo(seed.band)
        artists.select(R.id.filterBands).assertListed(seed.member, listed = false)

        artists
            .select(R.id.filterAll)
            .assertSelected(R.id.filterAll)
            .assertListed(seed.band, listed = true)
            .assertListed(seed.member, listed = true)
            .assertBothTypes()
    }

    @Test
    fun recreating_the_screen_keeps_the_selected_filter() {
        val seed = createCast()
        artists.enterAs(R.string.role_visitor).scrollTo(seed.band)
        artists.select(R.id.filterMusicians).assertListed(seed.band, listed = false)

        activityRule.scenario.recreate()

        artists
            .waitForList()
            .assertSelected(R.id.filterMusicians)
            .assertListed(seed.member, listed = true)
            .assertListed(seed.band, listed = false)
            .assertOnlyType(PerformerType.MUSICIAN)
    }

    @Test
    fun failing_load_without_saved_artists_shows_the_error_and_retry_loads_the_list() {
        val seed = createCast()
        artists.chooseRole(R.string.role_visitor)
        clearSavedArtists()
        firewall.block()

        artists.openArtistsTab().assertErrorShown()

        firewall.restore()
        artists.tapRetry().waitForList().scrollTo(seed.band)
        artists.assertRow(seed.band, overline = "Banda · desde 1991", summary = "2 álbumes · 1 integrante")
    }

    @Test
    fun refreshing_without_connection_keeps_the_list_and_shows_the_offline_notice() {
        val seed = createCast()
        artists.enterAs(R.string.role_visitor).scrollTo(seed.band)
        firewall.block()

        artists.pullToRefresh()

        waitForDisplayed(withText(R.string.artist_list_offline_notice))
        artists.assertListed(seed.band, listed = true).assertListed(seed.member, listed = true)
    }

    @Test
    fun pull_to_refresh_shows_a_performer_added_on_the_backend() {
        val seed = createCast()
        artists.enterAs(R.string.role_visitor).scrollTo(seed.band)
        val added = "E2E Nuevo $unique"
        data.createMusician(added, birthDate = "1985-03-03T00:00:00.000Z")

        artists
            .pullToRefresh()
            .assertRow(added, overline = "Músico · 1985", summary = "0 álbumes")
    }

    @Test
    fun tapping_a_row_opens_the_detail_with_its_id_and_type() {
        val seed = createCast()
        artists.enterAs(R.string.role_visitor).tapRow(seed.band)

        waitForDisplayed(withText(R.string.pending_next_sprint))
        activityRule.scenario.onActivity { activity ->
            val navController = navController(activity)
            val arguments = checkNotNull(navController.currentBackStackEntry?.arguments)
            assertEquals(R.id.performerDetailFragment, navController.currentDestination?.id)
            assertEquals(seed.bandId, arguments.getInt("performerId"))
            assertEquals("BAND", arguments.getString("type"))
        }
    }

    /** Un músico solo con un álbum, un integrante y su banda con dos álbumes, todos con nombres únicos. */
    private fun createCast(): Cast {
        data.createMusician("E2E Solo $unique", birthDate = "1960-06-06T00:00:00.000Z", albumCount = 1)
        val member = data.createMusician("E2E Miembro $unique")
        val bandId =
            data.createBand(
                name = "E2E Banda $unique",
                creationDate = "1991-05-05T00:00:00.000Z",
                albumCount = 2,
                members = listOf(member),
            )
        return Cast(
            solo = "E2E Solo $unique",
            member = "E2E Miembro $unique",
            band = "E2E Banda $unique",
            bandId = bandId,
        )
    }

    private fun navController(activity: FragmentActivity) =
        (activity.supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController

    // Deja Room sin artistas para que la carga siguiente no tenga nada que mostrar ni revalidar.
    private fun clearSavedArtists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as VinilosApplication).container
        runBlocking { container.performerDao.replaceAll(emptyList()) }
    }

    private data class Cast(
        val solo: String,
        val member: String,
        val band: String,
        val bandId: Int,
    )
}
