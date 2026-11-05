package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.AlbumStatus
import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.PerformerType
import com.google.gson.JsonParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("MagicNumber") // Fixture ids and counts.
class CollectorMappersTest {
    private val valid =
        CollectorDto(
            id = 100,
            name = "Manolo Bellon",
            email = "manollo@caracol.com.co",
            collectorAlbums = listOf(RelatedIdDto(1), RelatedIdDto(2), RelatedIdDto(3)),
            favoritePerformers = listOf(FavoritePerformerDto(id = 4), FavoritePerformerDto(id = 5)),
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

    @Test
    fun `detail joins both responses and recounts from the collections`() {
        val musician =
            FavoritePerformerDto(
                id = 100,
                name = "Rubén Blades",
                image = "https://photo",
                birthDate = "1948-07-16T00:00:00.000Z",
            )
        val band =
            FavoritePerformerDto(
                id = 101,
                name = "Queen",
                image = " ",
                creationDate = "1970-01-01T00:00:00.000Z",
            )
        val active = albumLink(albumId = 200, name = "Buscando América", price = 35, status = "Active")
        val inactive = albumLink(albumId = 201, name = "A Night at the Opera", price = 50, status = "Inactive")
        val dto =
            valid.copy(
                telephone = "3502457896",
                collectorAlbums = listOf(RelatedIdDto(1)),
                favoritePerformers = listOf(musician, band),
            )

        val detail = dto.toDetail(listOf(active, inactive))

        assertEquals(100, detail.collector.id)
        assertEquals("3502457896", detail.telephone)
        assertEquals(2, detail.collector.favoriteCount)
        assertEquals(2, detail.collector.albumCount)
        assertEquals(PerformerType.MUSICIAN, detail.favorites[0].type)
        assertEquals("https://photo", detail.favorites[0].imageUrl)
        assertEquals(PerformerType.BAND, detail.favorites[1].type)
        assertNull(detail.favorites[1].imageUrl)
        assertEquals(200, detail.albums[0].albumId)
        assertEquals(35, detail.albums[0].price)
        assertEquals(AlbumStatus.ACTIVE, detail.albums[0].status)
        assertEquals(201, detail.albums[1].albumId)
        assertEquals(AlbumStatus.INACTIVE, detail.albums[1].status)
    }

    @Test
    fun `empty or absent collections are a valid detail`() {
        val detail = valid.copy(telephone = "3502457896", favoritePerformers = null).toDetail(null)

        assertTrue(detail.favorites.isEmpty())
        assertTrue(detail.albums.isEmpty())
        assertEquals(0, detail.collector.favoriteCount)
        assertEquals(0, detail.collector.albumCount)
    }

    @Test
    fun `a detail without a required collector field is a serialization error`() {
        val ready = valid.copy(telephone = "3502457896")
        listOf(
            ready.copy(id = null),
            ready.copy(name = null),
            ready.copy(email = null),
            ready.copy(telephone = null),
            ready.copy(telephone = " "),
        ).forEach { dto ->
            assertThrows(JsonParseException::class.java) { dto.toDetail(emptyList()) }
        }
    }

    @Test
    fun `a favorite without identity or with both or neither date is a serialization error`() {
        val ready = valid.copy(telephone = "3502457896")
        val dated = FavoritePerformerDto(id = 1, name = "Queen", birthDate = "1948-07-16T00:00:00.000Z")
        listOf(
            null,
            dated.copy(id = null),
            dated.copy(name = " "),
            dated.copy(birthDate = null, creationDate = null),
            dated.copy(creationDate = "1970-01-01T00:00:00.000Z"),
        ).forEach { favorite ->
            val dto = ready.copy(favoritePerformers = listOf(favorite))
            assertThrows(JsonParseException::class.java) { dto.toDetail(emptyList()) }
        }
    }

    @Test
    fun `an album link without the nested album or a required field is a serialization error`() {
        val ready = valid.copy(telephone = "3502457896")
        val album = AlbumDto(id = 200, name = "Buscando América", cover = "https://cover")
        val link = CollectorAlbumDto(price = 35, status = "Active", album = album)
        listOf(
            null,
            link.copy(album = null),
            link.copy(album = album.copy(id = null)),
            link.copy(album = album.copy(name = " ")),
            link.copy(album = album.copy(cover = "")),
            link.copy(price = null),
            link.copy(status = "Sold"),
            link.copy(status = null),
        ).forEach { item ->
            assertThrows(JsonParseException::class.java) { ready.toDetail(listOf(item)) }
        }
    }

    private fun albumLink(
        albumId: Int,
        name: String,
        price: Int,
        status: String,
    ) = CollectorAlbumDto(
        price = price,
        status = status,
        album = AlbumDto(id = albumId, name = name, cover = "https://cover/$albumId"),
    )
}
