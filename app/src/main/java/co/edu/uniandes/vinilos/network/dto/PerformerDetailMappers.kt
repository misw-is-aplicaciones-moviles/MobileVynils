package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.models.PerformerDetail
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType
import com.google.gson.JsonParseException

internal fun MusicianDto.toDetailModel(): PerformerDetail =
    PerformerDetail(
        performer = toModel().validatedDetail(),
        description = description.orEmpty(),
        albums = albums.toDetailAlbums(),
        prizeAssignments = performerPrizes.orEmpty().map { it?.toModel() ?: missing("artist prize assignment") },
        members = emptyList(),
    )

internal fun BandDto.toDetailModel(): PerformerDetail =
    PerformerDetail(
        performer = toModel().validatedDetail(),
        description = description.orEmpty(),
        albums = albums.toDetailAlbums(),
        prizeAssignments = performerPrizes.orEmpty().map { it?.toModel() ?: missing("artist prize assignment") },
        members = musicians.orEmpty().map { member -> (member ?: missing("band musician")).toMemberSummary() },
    )

/** A band member as the detail shows it, read from `bands[].musicians` or from the add member answer. */
internal fun MusicianDto.toMemberSummary(): PerformerSummary =
    PerformerSummary(
        id = positiveId(id, "band musician id"),
        name = name?.takeIf { it.isNotBlank() } ?: missing("band musician name"),
        imageUrl = image,
        type = PerformerType.MUSICIAN,
    )

private fun Performer.validatedDetail(): Performer =
    also {
        positiveId(id, "performer id")
        if (name.isBlank()) throw JsonParseException("Blank performer name")
    }

private fun List<AlbumDto?>?.toDetailAlbums(): List<Album> =
    orEmpty().map { album ->
        val dto = album ?: missing("performer album")
        positiveId(dto.id, "performer album id")
        dto.toModel().also {
            if (it.name.isBlank()) throw JsonParseException("Blank performer album name")
        }
    }

private fun positiveId(
    id: Int?,
    field: String,
): Int = id?.takeIf { it > 0 } ?: throw JsonParseException("Missing or invalid $field")
