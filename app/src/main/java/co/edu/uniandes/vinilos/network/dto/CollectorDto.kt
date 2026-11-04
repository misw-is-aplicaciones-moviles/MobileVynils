package co.edu.uniandes.vinilos.network.dto

// Gson ignores Kotlin nullability, so every field is nullable and validated in the mappers.
// Only what the collector list draws is declared: the telephone and the comments are not read.
internal data class CollectorDto(
    val id: Int? = null,
    val name: String? = null,
    val email: String? = null,
    val collectorAlbums: List<RelatedIdDto>? = null,
    val favoritePerformers: List<RelatedIdDto>? = null,
)

/** A record nested in a collector: only its id is declared, because the list only counts them. */
internal data class RelatedIdDto(
    val id: Int? = null,
)
