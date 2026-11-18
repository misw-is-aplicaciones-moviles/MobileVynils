package co.edu.uniandes.vinilos.network.dto

// Gson ignores Kotlin nullability, so every field is nullable and validated in the mappers.
// The list only counts the nested records. HU06 reads the telephone, the favorites and the album links.
internal data class CollectorDto(
    val id: Int? = null,
    val name: String? = null,
    val telephone: String? = null,
    val email: String? = null,
    val collectorAlbums: List<RelatedIdDto>? = null,
    val favoritePerformers: List<FavoritePerformerDto?>? = null,
    // Only the ids: they tell who wrote each comment, which `GET /albums/:id` leaves out (HU09).
    val comments: List<RelatedIdDto>? = null,
)

/** A record nested in a collector: only its id is declared, because the list only counts them. */
internal data class RelatedIdDto(
    val id: Int? = null,
)

/** A favorite as `GET /collectors/:id` returns it. A musician has [birthDate]; a band has [creationDate]. */
internal data class FavoritePerformerDto(
    val id: Int? = null,
    val name: String? = null,
    val image: String? = null,
    val birthDate: String? = null,
    val creationDate: String? = null,
)

/**
 * One item of `GET /collectors/:id/albums`. The link id is not the album id: the album is nested,
 * because `GET /collectors/:id` does not include it.
 */
internal data class CollectorAlbumDto(
    val price: Int? = null,
    val status: String? = null,
    val album: AlbumDto? = null,
)
