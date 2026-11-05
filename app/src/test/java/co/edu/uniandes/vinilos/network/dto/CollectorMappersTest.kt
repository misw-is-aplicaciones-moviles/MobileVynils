package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.Collector
import com.google.gson.JsonParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

@Suppress("MagicNumber") // Fixture ids and counts.
class CollectorMappersTest {
    private val valid =
        CollectorDto(
            id = 100,
            name = "Manolo Bellon",
            email = "manollo@caracol.com.co",
            collectorAlbums = listOf(RelatedIdDto(1), RelatedIdDto(2), RelatedIdDto(3)),
            favoritePerformers = listOf(RelatedIdDto(4), RelatedIdDto(5)),
        )

    @Test
    fun `the counts come from the nested lists and do not get crossed`() {
        val expected = Collector(100, "Manolo Bellon", "manollo@caracol.com.co", albumCount = 3, favoriteCount = 2)

        assertEquals(expected, valid.toModel())
    }

    @Test
    fun `missing lists count as zero`() {
        val collector = valid.copy(collectorAlbums = null, favoritePerformers = null).toModel()

        assertEquals(0, collector.albumCount)
        assertEquals(0, collector.favoriteCount)
    }

    @Test
    fun `a collector without a required field is a serialization error`() {
        listOf(valid.copy(id = null), valid.copy(name = null), valid.copy(email = null)).forEach { dto ->
            assertThrows(JsonParseException::class.java) { dto.toModel() }
        }
    }
}
