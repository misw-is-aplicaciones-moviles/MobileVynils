package co.edu.uniandes.vinilos.models

import java.time.LocalDate

/** Album the collector wants to create; the backend assigns its id. */
data class NewAlbum(
    val name: String,
    val coverUrl: String,
    val releaseDate: LocalDate,
    val description: String,
    val genre: Genre,
    val recordLabel: RecordLabel,
)
