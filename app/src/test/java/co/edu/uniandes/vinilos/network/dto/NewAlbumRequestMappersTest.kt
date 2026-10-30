package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.RecordLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class NewAlbumRequestMappersTest {
    // Copied from the Joi schema of POST /albums in BackVynils (src/album/album.service.ts).
    private val backendGenres = listOf("Classical", "Salsa", "Rock", "Folk")
    private val backendRecordLabels = listOf("Sony Music", "EMI", "Discos Fuentes", "Elektra", "Fania Records")

    @Test
    fun `genres and record labels offered by the form are exactly the ones the backend accepts`() {
        val genres = Genre.entries.filter { it != Genre.UNKNOWN }.map { it.apiName }
        val recordLabels = RecordLabel.entries.map { it.apiName }

        assertEquals(backendGenres, genres)
        assertEquals(backendRecordLabels, recordLabels)
    }
}
