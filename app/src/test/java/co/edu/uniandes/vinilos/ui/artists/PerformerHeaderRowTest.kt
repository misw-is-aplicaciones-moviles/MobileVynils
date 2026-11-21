package co.edu.uniandes.vinilos.ui.artists

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.PerformerDetail
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PrizeAssignmentRef
import co.edu.uniandes.vinilos.testing.band
import co.edu.uniandes.vinilos.testing.musician
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class PerformerHeaderRowTest {
    private val date = LocalDate.of(1970, 1, 1)
    private val detail = PerformerDetail(band(1, "Banda"), "Biografía", emptyList(), emptyList())

    @Test
    fun `adding albums members or prizes does not change visible header content`() {
        val album = Album(2, "Álbum", "", date, "", Genre.ROCK, "", null, emptyList(), emptyList(), emptyList())
        val updated =
            detail.copy(
                performer = detail.performer.copy(albumCount = 1, memberCount = 1),
                albums = listOf(album),
                members = listOf(PerformerSummary(3, "Integrante")),
                prizeAssignments = listOf(PrizeAssignmentRef(4, date)),
            )
        assertEquals(detail.toHeaderRow(), updated.toHeaderRow())
    }

    @Test
    fun `each visible header field participates in content equality`() {
        val header = detail.toHeaderRow()
        val artist = detail.performer
        val changes =
            listOf(
                detail.copy(description = "Nueva biografía"),
                detail.copy(performer = artist.copy(name = "Otro nombre")),
                detail.copy(performer = artist.copy(imageUrl = "otra.jpg")),
                detail.copy(performer = artist.copy(date = date.plusYears(1))),
                detail.copy(performer = musician(1, "Banda", year = 1970)),
            )
        changes.forEach { assertNotEquals(header, it.toHeaderRow()) }
    }
}
