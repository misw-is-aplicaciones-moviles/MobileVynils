package co.edu.uniandes.vinilos.network.dto

// Gson ignores Kotlin nullability, so every field is nullable and validated in the mappers.
// Only what the artist list draws is declared: the albums and prizes of each performer are not read.
internal data class MusicianDto(
    val id: Int? = null,
    val name: String? = null,
    val image: String? = null,
    val birthDate: String? = null,
    val albums: List<RelatedIdDto>? = null,
)

internal data class BandDto(
    val id: Int? = null,
    val name: String? = null,
    val image: String? = null,
    val creationDate: String? = null,
    val albums: List<RelatedIdDto>? = null,
    val musicians: List<RelatedIdDto>? = null,
)

/** An album or musician nested in a performer: only its id is needed, to count it or to relate it. */
internal data class RelatedIdDto(
    val id: Int? = null,
)
