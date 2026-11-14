package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.PerformerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerformerSummaryMappersTest {
    @Test
    fun `album performer preserves musician or band type from the backend date fields`() {
        val musician = PerformerDto(id = 1, name = "Músico", birthDate = "1980-01-01T00:00:00.000Z")
        val band = PerformerDto(id = 2, name = "Banda", creationDate = "1990-01-01T00:00:00.000Z")

        assertEquals(PerformerType.MUSICIAN, musician.toModel().type)
        assertEquals(PerformerType.BAND, band.toModel().type)
    }

    @Test
    fun `old album responses keep their content without guessing an artist type`() {
        val summary = PerformerDto(id = 1, name = "Artista", image = "https://example.com/artist.png").toModel()

        assertEquals(1, summary.id)
        assertEquals("Artista", summary.name)
        assertEquals("https://example.com/artist.png", summary.imageUrl)
        assertNull(summary.type)
    }

    @Test
    fun `conflicting date fields do not guess an endpoint`() {
        val summary =
            PerformerDto(
                id = 1,
                name = "Ambiguo",
                birthDate = "1980-01-01T00:00:00.000Z",
                creationDate = "1990-01-01T00:00:00.000Z",
            ).toModel()

        assertNull(summary.type)
    }
}
