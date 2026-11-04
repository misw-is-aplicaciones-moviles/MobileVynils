package co.edu.uniandes.vinilos.database

import co.edu.uniandes.vinilos.testing.collector
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("MagicNumber") // Fixture ids and counts.
class CollectorEntityMappersTest {
    @Test
    fun `a round trip through the entity keeps every field and does not cross the counts`() {
        val manolo = collector(id = 100, name = "Manolo Bellon", email = "m@example.com", albums = 3, favorites = 2)

        val entity = manolo.toEntity()

        assertEquals(3, entity.albumCount)
        assertEquals(2, entity.favoriteCount)
        assertEquals(manolo, entity.toModel())
    }
}
