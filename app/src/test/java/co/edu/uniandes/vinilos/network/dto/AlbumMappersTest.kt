package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.Comment
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.Track
import com.google.gson.JsonParseException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

class AlbumMappersTest {
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setBogotaTimeZone() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `complete DTO maps every field`() {
        val album = fullDto.toModel()

        assertEquals(100, album.id)
        assertEquals("Buscando América", album.name)
        assertEquals("https://example.com/cover.jpg", album.coverUrl)
        assertEquals(LocalDate.of(1984, 8, 1), album.releaseDate)
        assertEquals("Description", album.description)
        assertEquals(Genre.SALSA, album.genre)
        assertEquals("Elektra", album.recordLabel)
        assertEquals("Rubén", album.performerName)
        assertEquals(listOf(Track(1, "Decisiones", "5:05")), album.tracks)
        assertEquals(listOf(PerformerSummary(7, "Rubén"), PerformerSummary(8, "Otro")), album.performers)
        assertEquals(listOf(Comment(3, "Great", 5)), album.comments)
    }

    @Test
    fun `release year is taken in UTC regardless of the device time zone`() {
        val album = fullDto.copy(releaseDate = "1984-01-01T00:00:00.000Z").toModel()

        assertEquals(1984, album.releaseDate.year)
    }

    @Test
    fun `performer name is the first performer in received order`() {
        assertEquals("Rubén", fullDto.toModel().performerName)
    }

    @Test
    fun `optional performer image is preserved for the detail avatar`() {
        val image = "https://example.com/artist.jpg"
        val dto = fullDto.copy(performers = listOf(PerformerDto(7, "Rubén", image)))

        assertEquals(
            image,
            dto
                .toModel()
                .performers
                .single()
                .imageUrl,
        )
    }

    @Test
    fun `empty or missing performers produce a null performer name`() {
        assertNull(fullDto.copy(performers = emptyList()).toModel().performerName)
        assertNull(fullDto.copy(performers = null).toModel().performerName)
    }

    @Test
    fun `unknown or missing genre maps to UNKNOWN`() {
        assertEquals(Genre.UNKNOWN, fullDto.copy(genre = "Jazz").toModel().genre)
        assertEquals(Genre.UNKNOWN, fullDto.copy(genre = null).toModel().genre)
    }

    @Test
    fun `missing optional fields use defaults`() {
        val album =
            AlbumDto(
                id = 1,
                name = "A",
                releaseDate = "1984-08-01T00:00:00.000Z",
                tracks = listOf(TrackDto(1, "T", null)),
            ).toModel()

        assertEquals("", album.coverUrl)
        assertEquals("", album.description)
        assertEquals("", album.recordLabel)
        assertEquals("", album.tracks.single().duration)
        assertTrue(album.performers.isEmpty())
        assertTrue(album.comments.isEmpty())
        assertEquals(0, CommentDto(id = 1, description = "x", rating = null).toModel().rating)
        assertEquals("", CommentDto(id = 1).toModel().description)
    }

    @Test(expected = JsonParseException::class)
    fun `missing album name is a JSON error`() {
        fullDto.copy(name = null).toModel()
    }

    @Test(expected = JsonParseException::class)
    fun `missing album id is a JSON error`() {
        fullDto.copy(id = null).toModel()
    }

    @Test(expected = JsonParseException::class)
    fun `missing release date is a JSON error`() {
        fullDto.copy(releaseDate = null).toModel()
    }

    @Test(expected = JsonParseException::class)
    fun `missing track name is a JSON error`() {
        fullDto.copy(tracks = listOf(TrackDto(id = 1))).toModel()
    }

    @Test(expected = JsonParseException::class)
    fun `missing performer id is a JSON error`() {
        fullDto.copy(performers = listOf(PerformerDto(name = "X"))).toModel()
    }

    @Test(expected = JsonParseException::class)
    fun `missing comment id is a JSON error`() {
        fullDto.copy(comments = listOf(CommentDto(description = "x"))).toModel()
    }

    @Test
    fun `invalid release date is a JSON error with a cause`() {
        try {
            fullDto.copy(releaseDate = "not a date").toModel()
            fail("Expected JsonParseException")
        } catch (error: JsonParseException) {
            assertNotNull(error.cause)
        }
    }

    private companion object {
        val fullDto =
            AlbumDto(
                id = 100,
                name = "Buscando América",
                cover = "https://example.com/cover.jpg",
                releaseDate = "1984-08-01T00:00:00.000Z",
                description = "Description",
                genre = "Salsa",
                recordLabel = "Elektra",
                tracks = listOf(TrackDto(1, "Decisiones", "5:05")),
                performers = listOf(PerformerDto(7, "Rubén"), PerformerDto(8, "Otro")),
                comments = listOf(CommentDto(3, "Great", 5)),
            )
    }
}
