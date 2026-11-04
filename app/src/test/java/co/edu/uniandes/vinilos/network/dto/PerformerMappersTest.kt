package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.testing.readResource
import com.google.gson.Gson
import com.google.gson.JsonParseException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

@Suppress("MagicNumber") // Seed ids, years and counts.
class PerformerMappersTest {
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
    fun `seed musician and band become typed performers with their counts`() {
        val performers = mergePerformers(musicians("api/musicians.json"), bands("api/bands.json"))

        assertEquals(listOf(100, 101), performers.map { it.id }.sorted())
        val ruben = performers.single { it.id == 100 }
        assertEquals(PerformerType.MUSICIAN, ruben.type)
        assertEquals("Rubén Blades Bellido de Luna", ruben.name)
        assertEquals(LocalDate.of(1948, 7, 16), ruben.date)
        assertEquals(2, ruben.albumCount)
        assertEquals(0, ruben.memberCount)
        assertNull(ruben.bandId)
        assertNull(ruben.bandName)
        val queen = performers.single { it.id == 101 }
        assertEquals(PerformerType.BAND, queen.type)
        assertEquals("Queen", queen.name)
        assertEquals(LocalDate.of(1970, 1, 1), queen.date)
        assertEquals(2, queen.albumCount)
        assertEquals(0, queen.memberCount)
    }

    @Test
    fun `musicians listed by a band belong to it and the others do not`() {
        val performers =
            mergePerformers(musicians("api/musicians_with_band.json"), bands("api/bands_with_members.json"))

        val queen = performers.single { it.type == PerformerType.BAND }
        assertEquals(4, queen.memberCount)
        val members = performers.filter { it.type == PerformerType.MUSICIAN && it.bandId == queen.id }
        val names = members.map { it.name }.sorted()
        assertEquals(listOf("Brian May", "Freddie Mercury", "John Deacon", "Roger Taylor"), names)
        assertTrue(members.all { it.bandName == "Queen" })
        val ruben = performers.single { it.name.startsWith("Rubén") }
        assertNull(ruben.bandId)
        assertNull(ruben.bandName)
    }

    @Test
    fun `a musician listed by two bands keeps the first band received`() {
        val musicians = listOf(musician(id = 7))
        val bands =
            listOf(
                band(id = 20, name = "Primera", members = listOf(7)),
                band(id = 21, name = "Segunda", members = listOf(7)),
            )

        val performer = mergePerformers(musicians, bands).single { it.id == 7 }

        assertEquals(20, performer.bandId)
        assertEquals("Primera", performer.bandName)
    }

    @Test
    fun `a band member that is not in the musician list is ignored`() {
        val performers = mergePerformers(listOf(musician(id = 7)), listOf(band(id = 20, members = listOf(7, 99))))

        assertEquals(listOf(7, 20), performers.map { it.id }.sorted())
        assertEquals(2, performers.single { it.id == 20 }.memberCount)
    }

    @Test
    fun `empty lists are an empty result`() {
        assertTrue(mergePerformers(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `only musicians or only bands still produce a list`() {
        assertEquals(listOf(7), mergePerformers(listOf(musician(id = 7)), emptyList()).map { it.id })
        assertEquals(listOf(20), mergePerformers(emptyList(), listOf(band(id = 20))).map { it.id })
    }

    @Test
    fun `year is taken in UTC regardless of the device time zone`() {
        // 05:00 UTC is midnight in Bogotá: still the same day. Midnight UTC is the previous day in Bogotá.
        val performers =
            mergePerformers(
                listOf(
                    musician(id = 1, birthDate = "1948-01-01T00:00:00.000Z"),
                    musician(id = 2, birthDate = "1970-01-01T05:00:00.000Z"),
                ),
                listOf(band(id = 3, creationDate = "1970-01-01T00:00:00.000Z")),
            )

        assertEquals(listOf(1948, 1970, 1970), performers.sortedBy { it.id }.map { it.date.year })
    }

    @Test
    fun `missing image and missing nested lists become empty values`() {
        val performers =
            mergePerformers(
                listOf(MusicianDto(id = 1, name = "Sin datos", birthDate = "1950-05-05T00:00:00.000Z")),
                listOf(BandDto(id = 2, name = "Banda sola", creationDate = "1990-01-01T00:00:00.000Z")),
            )

        performers.forEach {
            assertEquals("", it.imageUrl)
            assertEquals(0, it.albumCount)
            assertEquals(0, it.memberCount)
        }
    }

    @Test
    fun `a musician without a required field is a serialization error`() {
        listOf(
            musician(id = 1).copy(id = null),
            musician(id = 1).copy(name = null),
            musician(id = 1).copy(birthDate = null),
            musician(id = 1).copy(birthDate = "1948-07-16"),
        ).forEach { dto ->
            assertThrows(JsonParseException::class.java) { mergePerformers(listOf(dto), emptyList()) }
        }
    }

    @Test
    fun `a band without a required field is a serialization error`() {
        listOf(
            band(id = 1).copy(id = null),
            band(id = 1).copy(name = null),
            band(id = 1).copy(creationDate = null),
            band(id = 1).copy(creationDate = "yesterday"),
        ).forEach { dto ->
            assertThrows(JsonParseException::class.java) { mergePerformers(emptyList(), listOf(dto)) }
        }
    }

    @Test
    fun `a band member without an id is a serialization error`() {
        val band = band(id = 1).copy(musicians = listOf(RelatedIdDto()))

        assertThrows(JsonParseException::class.java) { mergePerformers(emptyList(), listOf(band)) }
    }

    @Test
    fun `an id shared by a musician and a band is rejected instead of overwriting one of them`() {
        assertThrows(JsonParseException::class.java) {
            mergePerformers(listOf(musician(id = 5)), listOf(band(id = 5)))
        }
    }

    private fun musicians(fixture: String): List<MusicianDto> =
        Gson().fromJson(readResource(fixture), Array<MusicianDto>::class.java).toList()

    private fun bands(fixture: String): List<BandDto> =
        Gson().fromJson(readResource(fixture), Array<BandDto>::class.java).toList()

    private fun musician(
        id: Int,
        birthDate: String = "1947-07-19T00:00:00.000Z",
    ) = MusicianDto(
        id = id,
        name = "Músico $id",
        image = "https://example.com/$id.jpg",
        birthDate = birthDate,
        albums = emptyList(),
    )

    private fun band(
        id: Int,
        name: String = "Banda $id",
        creationDate: String = "1970-01-01T00:00:00.000Z",
        members: List<Int> = emptyList(),
    ) = BandDto(
        id = id,
        name = name,
        image = "https://example.com/$id.jpg",
        creationDate = creationDate,
        albums = emptyList(),
        musicians = members.map { RelatedIdDto(it) },
    )
}
