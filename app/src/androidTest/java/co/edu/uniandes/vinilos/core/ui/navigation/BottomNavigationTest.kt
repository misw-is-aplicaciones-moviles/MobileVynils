package co.edu.uniandes.vinilos.core.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** CP-TR-06: la barra inferior tiene tres pestañas y conserva su estado al volver. */
@RunWith(AndroidJUnit4::class)
class BottomNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun text(
        @StringRes id: Int,
    ): String = composeRule.activity.getString(id)

    // La etiqueta de la pestaña va en mayúsculas y coincide con el título de su pantalla
    // salvo por el caso: se distingue por ser el nodo con acción de clic.
    private fun tab(
        @StringRes id: Int,
    ): SemanticsNodeInteraction = composeRule.onNode(hasText(text(id), ignoreCase = true) and hasClickAction())

    private fun title(
        @StringRes id: Int,
    ): SemanticsNodeInteraction = composeRule.onNodeWithText(text(id))

    @Test
    fun cp_tr_06_bottom_bar_switches_between_the_three_tabs() {
        tab(R.string.nav_albums).assertIsDisplayed().assertIsSelected()
        tab(R.string.nav_artists).assertIsDisplayed()
        tab(R.string.nav_collectors).assertIsDisplayed()
        title(R.string.album_list_title).assertIsDisplayed()

        tab(R.string.nav_artists).performClick()
        title(R.string.artist_list_title).assertIsDisplayed()
        title(R.string.pending_next_sprint).assertIsDisplayed()
        tab(R.string.nav_artists).assertIsSelected()

        tab(R.string.nav_collectors).performClick()
        title(R.string.collector_list_title).assertIsDisplayed()
        title(R.string.pending_next_sprint).assertIsDisplayed()

        tab(R.string.nav_albums).performClick()
        title(R.string.album_list_title).assertIsDisplayed()
        tab(R.string.nav_albums).assertIsSelected()
    }
}
