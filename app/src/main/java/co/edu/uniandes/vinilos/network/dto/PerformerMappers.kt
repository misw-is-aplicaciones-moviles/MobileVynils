package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.models.PerformerType
import com.google.gson.JsonParseException

/**
 * Joins `GET /musicians` and `GET /bands` into one list of typed performers.
 * `GET /musicians` does not load the band of a musician, so the membership comes from `bands[].musicians`.
 * A musician listed by two bands keeps the first one. Ids are shared by both types in the backend: a repeated id
 * means the response is not trustworthy, and it is rejected instead of letting one performer overwrite another.
 */
internal fun mergePerformers(
    musicians: List<MusicianDto>,
    bands: List<BandDto>,
): List<Performer> {
    val bandModels = bands.map { it.toModel() }
    val bandByMusicianId = mutableMapOf<Int, Performer>()
    bands.zip(bandModels).forEach { (dto, band) ->
        dto.musicians.orEmpty().forEach { member ->
            bandByMusicianId.putIfAbsent(member.id ?: missing("band musician id"), band)
        }
    }
    val musicianModels =
        musicians.map { dto ->
            val model = dto.toModel()
            val band = bandByMusicianId[model.id]
            model.copy(bandId = band?.id, bandName = band?.name)
        }
    val performers = musicianModels + bandModels
    if (performers.map { it.id }.toSet().size != performers.size) {
        throw JsonParseException("Duplicated performer id")
    }
    return performers
}

internal fun MusicianDto.toModel(): Performer =
    Performer(
        id = id ?: missing("musician id"),
        type = PerformerType.MUSICIAN,
        name = name ?: missing("musician name"),
        imageUrl = image.orEmpty(),
        date = parseUtcDate(birthDate ?: missing("musician birthDate"), "musician birthDate"),
        albumCount = albums.orEmpty().size,
        memberCount = 0,
        bandId = null,
        bandName = null,
    )

internal fun BandDto.toModel(): Performer =
    Performer(
        id = id ?: missing("band id"),
        type = PerformerType.BAND,
        name = name ?: missing("band name"),
        imageUrl = image.orEmpty(),
        date = parseUtcDate(creationDate ?: missing("band creationDate"), "band creationDate"),
        albumCount = albums.orEmpty().size,
        memberCount = musicians.orEmpty().size,
        bandId = null,
        bandName = null,
    )
