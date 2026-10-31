package co.edu.uniandes.vinilos.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `width alone keeps the 71 to 48 ratio of the catalog`() {
        val (width, height) = VinylArtworkView.fitSize(142, null, ratioWidth = 71, ratioHeight = 48)

        assertEquals(142, width)
        assertEquals(96, height)
    }

    @Test
    fun `height alone derives the width with the 71 to 48 ratio`() {
        val (width, height) = VinylArtworkView.fitSize(null, 96, ratioWidth = 71, ratioHeight = 48)

        assertEquals(142, width)
        assertEquals(96, height)
    }

    @Test
    fun `parseRatio reads width and height`() {
        assertEquals(71 to 48, VinylArtworkView.parseRatio("71:48"))
        assertEquals(33 to 20, VinylArtworkView.parseRatio(" 33 : 20 "))
    }

    @Test
    fun `parseRatio rejects a value that is not width colon height`() {
        listOf("", "71", "71:", "a:b", "71:48:2", "0:20", "33:-1").forEach { invalid ->
            val error = runCatching { VinylArtworkView.parseRatio(invalid) }.exceptionOrNull()

            assertTrue("\"$invalid\" must fail", error is IllegalArgumentException)
            assertTrue(error?.message.orEmpty().contains("artworkRatio"))
        }
    }
}
