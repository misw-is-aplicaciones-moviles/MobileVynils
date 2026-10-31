package co.edu.uniandes.vinilos.models

import java.time.LocalDate

/** An album of the catalog; [performerName] is the first performer received, if any. */
data class Album(
    val id: Int,
    val name: String,
    val coverUrl: String,
    val releaseDate: LocalDate,
    val description: String,
    val genre: Genre,
    val recordLabel: String,
    val performerName: String?,
    val tracks: List<Track>,
    val performers: List<PerformerSummary>,
    val comments: List<Comment>,
)
