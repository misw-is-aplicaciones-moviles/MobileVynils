package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.NewAlbum

// Exactly the six fields of the backend schema: BackVynils answers 400 to any extra field, such as id.
internal data class NewAlbumRequest(
    val name: String,
    val cover: String,
    val releaseDate: String,
    val description: String,
    val genre: String,
    val recordLabel: String,
)

// Midnight UTC, the same instant parseReleaseDate reads back, so the day never shifts in UTC-5.
internal fun NewAlbum.toRequest(): NewAlbumRequest =
    NewAlbumRequest(
        name = name,
        cover = coverUrl,
        releaseDate = "${releaseDate}T00:00:00.000Z",
        description = description,
        genre = genre.apiName,
        recordLabel = recordLabel.apiName,
    )
