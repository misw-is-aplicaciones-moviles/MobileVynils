package co.edu.uniandes.vinilos.ui.albums

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Comment
import co.edu.uniandes.vinilos.models.CommentAuthor
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class AlbumHeaderRowTest {
    private val album =
        Album(
            id = 100,
            name = "Buscando América",
            coverUrl = "https://example.com/cover.jpg",
            releaseDate = LocalDate.of(1984, 8, 1),
            description = "Primer álbum de la banda.",
            genre = Genre.SALSA,
            recordLabel = "Elektra",
            performerName = "Rubén Blades",
            tracks = listOf(Track(1, "Decisiones", "5:05")),
            performers = listOf(PerformerSummary(7, "Rubén Blades", RUBEN_IMAGE, PerformerType.MUSICIAN)),
            comments = listOf(Comment(1, "Muy bueno", 5)),
        )

    @Test
    fun the_header_keeps_only_what_it_draws() {
        val header = album.toHeaderRow()

        assertEquals("Buscando América", header.name)
        assertEquals("https://example.com/cover.jpg", header.coverUrl)
        assertEquals("Salsa", header.genreName)
        assertEquals(LocalDate.of(1984, 8, 1), header.releaseDate)
        assertEquals("Rubén Blades", header.performerName)
        assertEquals("Primer álbum de la banda.", header.description)
        assertEquals("Elektra", header.recordLabel)
        assertEquals(1, header.trackCount)
        assertEquals(album.performers.first(), header.artist)
        assertEquals("header", header.key)
    }

    @Test
    fun a_new_comment_leaves_the_header_equal_so_it_is_not_bound_again() {
        val withComment =
            album.copy(comments = album.comments + Comment(2, "Excelente", 4, CommentAuthor(3, "Manolo Bellon")))

        assertEquals(album.toHeaderRow(), withComment.toHeaderRow())
    }

    @Test
    fun a_new_track_changes_the_visible_count_so_the_header_is_bound_again() {
        val withTrack = album.copy(tracks = album.tracks + Track(2, "Todos vuelven", "5:25"))

        assertNotEquals(album.toHeaderRow(), withTrack.toHeaderRow())
        assertEquals(2, withTrack.toHeaderRow().trackCount)
    }

    @Test
    fun changing_a_visible_field_changes_the_header() {
        val header = album.toHeaderRow()

        assertNotEquals(header, album.copy(name = "Siembra").toHeaderRow())
        assertNotEquals(header, album.copy(coverUrl = "https://example.com/other.jpg").toHeaderRow())
        assertNotEquals(header, album.copy(performers = emptyList(), performerName = null).toHeaderRow())
    }

    @Test
    fun an_album_without_performers_has_no_artist_to_open() {
        val header = album.copy(performers = emptyList(), performerName = null).toHeaderRow()

        assertEquals(null, header.artist)
        assertEquals(null, header.performerName)
    }

    private companion object {
        const val RUBEN_IMAGE = "https://example.com/ruben.jpg"
    }
}
