package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Comment
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.Track
import com.google.gson.JsonParseException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

internal fun AlbumDto.toModel(): Album {
    val performerSummaries = performers.orEmpty().map { it.toModel() }
    return Album(
        id = id ?: missing("album id"),
        name = name ?: missing("album name"),
        coverUrl = cover.orEmpty(),
        releaseDate = parseReleaseDate(releaseDate ?: missing("album releaseDate")),
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

internal fun PerformerDto.toModel(): PerformerSummary =
    PerformerSummary(
        id = id ?: missing("performer id"),
        name = name ?: missing("performer name"),
    )

internal fun CommentDto.toModel(): Comment =
    Comment(
        id = id ?: missing("comment id"),
        description = description.orEmpty(),
        rating = rating ?: 0,
    )

// The year is read in UTC: the backend sends midnight UTC, which is the previous day in UTC-5.
private fun parseReleaseDate(text: String): LocalDate =
    try {
        Instant.parse(text).atOffset(ZoneOffset.UTC).toLocalDate()
    } catch (error: DateTimeParseException) {
        throw JsonParseException("Invalid album releaseDate: $text", error)
    }

private fun missing(field: String): Nothing = throw JsonParseException("Missing required field: $field")
