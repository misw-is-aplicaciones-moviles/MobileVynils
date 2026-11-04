package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.PerformerType

/** What one artist row needs; the adapter only joins these values with string resources. */
data class PerformerItem(
    val id: Int,
    val type: PerformerType,
    val name: String,
    val imageUrl: String,
    /** Birth year of a musician or founding year of a band, read in UTC. */
    val year: Int,
    val summary: PerformerSummaryLine,
)

/** Which third line a row shows. The adapter turns it into text with plurals, so the rule is testable without Android. */
sealed interface PerformerSummaryLine {
    /** A band: "2 álbumes · 4 integrantes". */
    data class BandCounts(
        val albums: Int,
        val members: Int,
    ) : PerformerSummaryLine

    /** A musician that belongs to a band: "Integrante de Queen". */
    data class MemberOf(
        val bandName: String,
    ) : PerformerSummaryLine

    /** A musician without a band: "2 álbumes". */
    data class AlbumCount(
        val albums: Int,
    ) : PerformerSummaryLine
}
