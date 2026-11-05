package co.edu.uniandes.vinilos.database

import co.edu.uniandes.vinilos.database.entities.CollectorEntity
import co.edu.uniandes.vinilos.models.Collector

internal fun Collector.toEntity(): CollectorEntity =
    CollectorEntity(
        id = id,
        name = name,
        email = email,
        albumCount = albumCount,
        favoriteCount = favoriteCount,
    )

internal fun CollectorEntity.toModel(): Collector =
    Collector(
        id = id,
        name = name,
        email = email,
        albumCount = albumCount,
        favoriteCount = favoriteCount,
    )
