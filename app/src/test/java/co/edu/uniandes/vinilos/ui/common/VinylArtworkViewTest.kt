package co.edu.uniandes.vinilos.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("MagicNumber")
class VinylArtworkViewTest {
    @Test
    fun `width alone keeps the 33 to 20 ratio`() {
        val (width, height) = VinylArtworkView.fitSize(maxWidth = 330, maxHeight = null)

        assertEquals(330, width)
        assertEquals(200, height)
    }

    @Test
    fun `a short bound shrinks the width and keeps the ratio`() {
        val (width, height) = VinylArtworkView.fitSize(maxWidth = 330, maxHeight = 100)

        assertEquals(165, width)
        assertEquals(100, height)
    }

    @Test
    fun `height alone derives the width`() {
        val (width, height) = VinylArtworkView.fitSize(maxWidth = null, maxHeight = 200)

        assertEquals(330, width)
        assertEquals(200, height)
    }
}
