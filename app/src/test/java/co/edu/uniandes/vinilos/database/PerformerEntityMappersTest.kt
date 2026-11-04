package co.edu.uniandes.vinilos.database

import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.testing.band
import co.edu.uniandes.vinilos.testing.musician
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

@Suppress("MagicNumber") // Fixture ids and years.
class PerformerEntityMappersTest {
    private val queen = band(id = 101, name = "Queen", year = 1970, albums = 2, members = 4)
    private val brian = musician(id = 103, name = "Brian May", bandId = 101, bandName = "Queen")

    @Test
    fun `entity stores the type name and the band id but not the band name`() {
        val entity = brian.toEntity()

        assertEquals("MUSICIAN", entity.type)
        assertEquals(101, entity.bandId)
        assertEquals(LocalDate.of(1947, 1, 1), entity.date)
        assertEquals("BAND", queen.toEntity().type)
        assertEquals(4, queen.toEntity().memberCount)
    }

    @Test
    fun `round trip of the whole list restores the band name of its members`() {
        val restored = listOf(brian, queen).map { it.toEntity() }.toModels()

        assertEquals(listOf(brian, queen), restored)
    }

    @Test
    fun `a musician whose band is not in the list has no band name`() {
        val restored = listOf(brian.toEntity()).toModels().single()

        assertEquals(101, restored.bandId)
        assertNull(restored.bandName)
    }

    @Test
    fun `a band never gets a band name`() {
        val restored = listOf(queen.toEntity()).toModels().single()

        assertEquals(PerformerType.BAND, restored.type)
        assertNull(restored.bandId)
        assertNull(restored.bandName)
    }

    @Test
    fun `a musician without a band round trips with no band`() {
        val ruben = musician(id = 100, name = "Rubén", year = 1948, albums = 2)

        assertEquals(ruben, listOf(ruben.toEntity()).toModels().single())
    }
}
