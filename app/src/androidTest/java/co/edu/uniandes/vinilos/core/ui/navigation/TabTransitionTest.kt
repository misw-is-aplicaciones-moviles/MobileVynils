package co.edu.uniandes.vinilos.core.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La transición entre pestañas no debe parpadear: durante los 300 ms, la hoja crema nunca deja ver
 * el fondo carbón del contenedor. Se congela el reloj de animación y se revisa cuadro a cuadro.
 */
@RunWith(AndroidJUnit4::class)
class TabTransitionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun tab(
        @StringRes id: Int,
    ) = composeRule.onNode(hasText(composeRule.activity.getString(id), ignoreCase = true) and hasClickAction())

    /** Luminancia de un punto de la hoja, en el centro horizontal y a media altura de la pantalla. */
    private fun sheetLuminance(): Float {
        val image = composeRule.onRoot().captureToImage()
        val pixels = image.toPixelMap()
        val color: Color = pixels[image.width / 2, image.height / 2]
        return color.luminance()
    }

    @Test
    fun switching_tabs_keeps_the_cream_sheet_visible_on_every_frame() {
        composeRule.waitForIdle()
        val restingLuminance = sheetLuminance()
        composeRule.mainClock.autoAdvance = false

        listOf(R.string.nav_artists, R.string.nav_collectors, R.string.nav_albums).forEach { destination ->
            tab(destination).performClick()
            var elapsed = 0L
            while (elapsed <= TRANSITION_MILLIS + FRAME_MILLIS) {
                val luminance = sheetLuminance()
                assertTrue(
                    "Parpadeo a $elapsed ms: luminancia $luminance frente a $restingLuminance en reposo",
                    luminance > restingLuminance * MIN_RATIO,
                )
                composeRule.mainClock.advanceTimeBy(FRAME_MILLIS)
                elapsed += FRAME_MILLIS
            }
        }
        composeRule.mainClock.autoAdvance = true
    }

    /** Píxeles de tinta visibles (más claros que el fondo carbón) en la franja del título. */
    private fun titleInkPixels(band: Rect): Int {
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        var count = 0
        for (y in band.top.toInt() until band.bottom.toInt()) {
            for (x in band.left.toInt() until band.right.toInt()) {
                if (pixels[x, y].luminance() > INK_LUMINANCE) count++
            }
        }
        return count
    }

    private fun titleBounds(
        @StringRes id: Int,
    ): Rect = composeRule.onNodeWithText(composeRule.activity.getString(id)).fetchSemanticsNode().boundsInRoot

    @Test
    fun switching_tabs_never_draws_two_titles_at_once() {
        composeRule.waitForIdle()
        val albumsBand = titleBounds(R.string.album_list_title)
        val albumsInk = titleInkPixels(albumsBand)
        tab(R.string.nav_artists).performClick()
        composeRule.waitForIdle()
        val artistsBand = titleBounds(R.string.artist_list_title)
        val band = Rect(albumsBand.left, albumsBand.top, maxOf(albumsBand.right, artistsBand.right), albumsBand.bottom)
        val artistsInk = titleInkPixels(band)
        val singleTitleMax = maxOf(albumsInk, titleInkPixels(band).coerceAtLeast(artistsInk))

        composeRule.mainClock.autoAdvance = false
        tab(R.string.nav_albums).performClick()
        var elapsed = 0L
        while (elapsed <= TRANSITION_MILLIS + FRAME_MILLIS) {
            val ink = titleInkPixels(band)
            assertTrue(
                "Títulos superpuestos a $elapsed ms: $ink píxeles de tinta frente a $singleTitleMax de un solo título",
                ink <= singleTitleMax * MAX_INK_RATIO,
            )
            composeRule.mainClock.advanceTimeBy(FRAME_MILLIS)
            elapsed += FRAME_MILLIS
        }
        composeRule.mainClock.autoAdvance = true
    }

    private companion object {
        // Luminancia del carbón ~0,02; cualquier trazo de texto crema, aun a media opacidad, la supera.
        const val INK_LUMINANCE = 0.1f
        const val MAX_INK_RATIO = 1.1f

        const val FRAME_MILLIS = 16L

        // El texto provisional en el centro baja la luminancia del punto solo un poco; el carbón la hunde.
        const val MIN_RATIO = 0.85f
    }
}
