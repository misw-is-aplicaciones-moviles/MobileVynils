package co.edu.uniandes.vinilos.network.dto

// Gson ignores Kotlin nullability, so every field is nullable and validated in the mappers.
internal data class AlbumDto(
    val id: Int? = null,
    val name: String? = null,
    val cover: String? = null,
    val releaseDate: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val recordLabel: String? = null,
    val tracks: List<TrackDto>? = null,
    val performers: List<PerformerDto>? = null,
    val comments: List<CommentDto>? = null,
)

internal data class TrackDto(
    val id: Int? = null,
    val name: String? = null,
    val duration: String? = null,
)

internal data class PerformerDto(
    val id: Int? = null,
    val name: String? = null,
    val image: String? = null,
)

internal data class CommentDto(
    val id: Int? = null,
    val description: String? = null,
    val rating: Int? = null,
)
