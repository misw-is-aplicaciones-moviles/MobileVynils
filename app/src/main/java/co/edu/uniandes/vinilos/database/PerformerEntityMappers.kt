package co.edu.uniandes.vinilos.database

import co.edu.uniandes.vinilos.database.entities.PerformerEntity
import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.models.PerformerType

internal fun Performer.toEntity(): PerformerEntity =
    PerformerEntity(
        id = id,
        type = type.name,
        name = name,
        image = imageUrl,
        date = date,
        albumCount = albumCount,
        memberCount = memberCount,
        bandId = bandId,
    )

/**
 * Converts the whole table at once because the name of a musician's band is not stored on its row:
 * it is looked up among the bands of the same list. A band that is no longer in the list leaves the name empty.
 */
internal fun List<PerformerEntity>.toModels(): List<Performer> {
    val bandNames = filter { it.type == PerformerType.BAND.name }.associate { it.id to it.name }
    return map { entity ->
        Performer(
            id = entity.id,
            type = PerformerType.valueOf(entity.type),
            name = entity.name,
            imageUrl = entity.image,
            date = entity.date,
            albumCount = entity.albumCount,
            memberCount = entity.memberCount,
            bandId = entity.bandId,
            bandName = entity.bandId?.let(bandNames::get),
        )
    }
}
