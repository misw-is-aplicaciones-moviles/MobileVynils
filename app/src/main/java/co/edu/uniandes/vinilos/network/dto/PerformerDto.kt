package co.edu.uniandes.vinilos.network.dto

// Gson ignores Kotlin nullability, so every field is nullable and validated in the mappers.
// The list counts relationships; HU04 also reads their detail. Prize handling belongs to HU14.
internal data class MusicianDto(
    val id: Int? = null,
    val name: String? = null,
    val image: String? = null,
    val birthDate: String? = null,
    val description: String? = null,
    val albums: List<AlbumDto?>? = null,
)

internal data class BandDto(
    val id: Int? = null,
    val name: String? = null,
    val image: String? = null,
    val creationDate: String? = null,
    val description: String? = null,
    val albums: List<AlbumDto?>? = null,
    val musicians: List<MusicianDto?>? = null,
)
