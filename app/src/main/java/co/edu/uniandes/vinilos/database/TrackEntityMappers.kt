package co.edu.uniandes.vinilos.database

import co.edu.uniandes.vinilos.database.entities.TrackEntity
import co.edu.uniandes.vinilos.models.Track

internal fun Track.toEntity(albumId: Int): TrackEntity =
    TrackEntity(
        id = id,
        name = name,
        duration = duration,
        albumId = albumId,
    )

internal fun TrackEntity.toModel(): Track =
    Track(
        id = id,
        name = name,
        duration = duration,
    )
