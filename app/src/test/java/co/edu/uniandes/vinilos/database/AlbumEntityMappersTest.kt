package co.edu.uniandes.vinilos.database

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Comment
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AlbumEntityMappersTest {
    @Test
    fun `round trip keeps performer name and genre`() {
        val restored = album.toEntity().toModel()

        assertEquals("Rubén", restored.performerName)
        assertEquals(Genre.SALSA, restored.genre)
        assertEquals(album.coverUrl, restored.coverUrl)
        assertEquals(album.releaseDate, restored.releaseDate)
    }

    @Test
    fun `round trip keeps a null performer name`() {
        val restored = album.copy(performerName = null).toEntity().toModel()

        assertNull(restored.performerName)
    }

    @Test
    fun `entity stores the API names`() {
        val entity = album.toEntity()

        assertEquals("Salsa", entity.genre)
        assertEquals(album.coverUrl, entity.cover)
    }

    @Test
    fun `entity with unknown genre maps to UNKNOWN`() {
        val entity = album.toEntity().copy(genre = "Jazz")

        assertEquals(Genre.UNKNOWN, entity.toModel().genre)
    }

    @Test
    fun `model read from Room has empty lists`() {
        val restored = album.toEntity().toModel()

        assertTrue(restored.tracks.isEmpty())
        assertTrue(restored.performers.isEmpty())
        assertTrue(restored.comments.isEmpty())
    }

    private companion object {
        val album =
            Album(
                id = 100,
                name = "Buscando América",
                coverUrl = "https://example.com/cover.jpg",
                releaseDate = LocalDate.of(1984, 8, 1),
                description = "Description",
                genre = Genre.SALSA,
                recordLabel = "Elektra",
                performerName = "Rubén",
                tracks = listOf(Track(1, "Decisiones", "5:05")),
                performers = listOf(PerformerSummary(7, "Rubén")),
                comments = listOf(Comment(3, "Great", 5)),
            )
    }
}
