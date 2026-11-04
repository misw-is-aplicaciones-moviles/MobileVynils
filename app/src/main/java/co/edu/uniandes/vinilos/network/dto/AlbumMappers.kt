package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Comment
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.Track

internal fun AlbumDto.toModel(): Album {
    val performerSummaries = performers.orEmpty().map { it.toModel() }
    return Album(
        id = id ?: missing("album id"),
        name = name ?: missing("album name"),
        coverUrl = cover.orEmpty(),
        releaseDate = parseUtcDate(releaseDate ?: missing("album releaseDate"), "album releaseDate"),
        description = description.orEmpty(),
        genre = Genre.fromApiName(genre),
        recordLabel = recordLabel.orEmpty(),
        performerName = performerSummaries.firstOrNull()?.name,
        tracks = tracks.orEmpty().map { it.toModel() },
        performers = performerSummaries,
        comments = comments.orEmpty().map { it.toModel() },
    )
}

internal fun TrackDto.toModel(): Track =
    Track(
        id = id ?: missing("track id"),
        name = name ?: missing("track name"),
        duration = duration.orEmpty(),
    )

internal fun TrackDto.toCreatedTrackModel(): Track =
    Track(
        id = id ?: missing("track id"),
        name = name ?: missing("track name"),
        duration = duration ?: missing("track duration"),
    )

internal fun PerformerDto.toModel(): PerformerSummary =
    PerformerSummary(
        id = id ?: missing("performer id"),
        name = name ?: missing("performer name"),
        imageUrl = image,
        type =
            when {
                birthDate != null && creationDate == null -> PerformerType.MUSICIAN
                creationDate != null && birthDate == null -> PerformerType.BAND
                else -> null
            },
    )

internal fun CommentDto.toModel(): Comment =
    Comment(
        id = id ?: missing("comment id"),
        description = description.orEmpty(),
        rating = rating ?: 0,
    )
