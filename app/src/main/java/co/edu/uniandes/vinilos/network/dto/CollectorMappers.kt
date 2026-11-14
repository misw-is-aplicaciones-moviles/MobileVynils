package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.AlbumStatus
import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.CollectorAlbum
import co.edu.uniandes.vinilos.models.CollectorDetail
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType

internal fun CollectorDto.toModel(): Collector =
    Collector(
        id = id ?: missing("collector id"),
        name = name ?: missing("collector name"),
        email = email ?: missing("collector email"),
        albumCount = collectorAlbums.orEmpty().size,
        favoriteCount = favoritePerformers.orEmpty().size,
    )

/** Joins `GET /collectors/:id` with `GET /collectors/:id/albums`. Counts come from those collections. */
internal fun CollectorDto.toDetail(albumLinks: List<CollectorAlbumDto?>?): CollectorDetail {
    val favorites = favoritePerformers.orEmpty().map { it.toSummary() }
    val albums = albumLinks.orEmpty().map { it.toCollectorAlbum() }
    return CollectorDetail(
        collector =
            Collector(
                id = id ?: missing("collector id"),
                name = name ?: missing("collector name"),
                email = email ?: missing("collector email"),
                albumCount = albums.size,
                favoriteCount = favorites.size,
            ),
        telephone = telephone?.takeIf { it.isNotBlank() } ?: missing("collector telephone"),
        favorites = favorites,
        albums = albums,
    )
}

private fun FavoritePerformerDto?.toSummary(): PerformerSummary {
    val favorite = this ?: missing("favorite performer")
    return PerformerSummary(
        id = favorite.id ?: missing("favorite performer id"),
        name = favorite.name?.takeIf { it.isNotBlank() } ?: missing("favorite performer name"),
        imageUrl = favorite.image?.takeIf { it.isNotBlank() },
        type = favoriteType(favorite.birthDate, favorite.creationDate),
    )
}

/** A musician carries only [birthDate] and a band only [creationDate]. Either both or neither is rejected. */
private fun favoriteType(
    birthDate: String?,
    creationDate: String?,
): PerformerType {
    val musician = !birthDate.isNullOrBlank()
    val band = !creationDate.isNullOrBlank()
    if (musician == band) missing("favorite performer type")
    return if (musician) PerformerType.MUSICIAN else PerformerType.BAND
}

private fun CollectorAlbumDto?.toCollectorAlbum(): CollectorAlbum {
    val link = this ?: missing("collector album")
    val album = link.album ?: missing("collector album")
    return CollectorAlbum(
        albumId = album.id ?: missing("album id"),
        name = album.name?.takeIf { it.isNotBlank() } ?: missing("album name"),
        coverUrl = album.cover?.takeIf { it.isNotBlank() } ?: missing("album cover"),
        price = link.price ?: missing("collector album price"),
        status = albumStatus(link.status),
    )
}

private fun albumStatus(value: String?): AlbumStatus =
    when (value) {
        "Active" -> AlbumStatus.ACTIVE
        "Inactive" -> AlbumStatus.INACTIVE
        else -> missing("collector album status")
    }
