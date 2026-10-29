package co.edu.uniandes.vinilos.database

import co.edu.uniandes.vinilos.database.entities.AlbumEntity
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Genre

internal fun Album.toEntity(): AlbumEntity =
    AlbumEntity(
        id = id,
        name = name,
        cover = coverUrl,
        releaseDate = releaseDate,
        description = description,
        genre = genre.apiName,
        recordLabel = recordLabel,
        performerName = performerName,
    )

// Room only stores the list summary, so tracks, performers and comments come back empty.
internal fun AlbumEntity.toModel(): Album =
    Album(
        id = id,
        name = name,
        coverUrl = cover,
        releaseDate = releaseDate,
        description = description,
        genre = Genre.fromApiName(genre),
        recordLabel = recordLabel,
        performerName = performerName,
        tracks = emptyList(),
        performers = emptyList(),
        comments = emptyList(),
    )
