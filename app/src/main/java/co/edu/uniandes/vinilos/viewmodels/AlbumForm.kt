package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.RecordLabel
import java.time.LocalDate

/** What the collector has typed or picked so far; any field may still be empty. */
data class AlbumForm(
    val name: String = "",
    val coverUrl: String = "",
    val releaseDate: LocalDate? = null,
    val description: String = "",
    val genre: Genre? = null,
    val recordLabel: RecordLabel? = null,
)
