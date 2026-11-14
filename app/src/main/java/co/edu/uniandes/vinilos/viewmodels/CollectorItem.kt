package co.edu.uniandes.vinilos.viewmodels

import java.util.Locale

/** What one collector row needs; the adapter only joins these values with string resources. */
data class CollectorItem(
    val id: Int,
    /** Place in the list with two digits: "01". */
    val position: String,
    val initials: String,
    val name: String,
    val email: String,
    val summary: CollectorSummaryLine,
)

/** Which third line a row shows. The adapter turns it into text with plurals, so the rule is testable without Android. */
sealed interface CollectorSummaryLine {
    /** "3 álbumes · 2 favoritos". */
    data class Counts(
        val albums: Int,
        val favorites: Int,
    ) : CollectorSummaryLine

    /** "Sin álbumes aún", even if the collector has favorites (docs/10). */
    data object NoAlbums : CollectorSummaryLine
}

private val WHITESPACE = "\\s+".toRegex()

/** First letter of the first and of the last word: "Ana Lucía Pardo" is "AP". A single word gives one letter. */
internal fun collectorInitials(name: String): String {
    val words = name.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    return listOfNotNull(words.firstOrNull(), words.drop(1).lastOrNull())
        .joinToString("") { it.take(1) }
        .uppercase(Locale.ROOT)
}
