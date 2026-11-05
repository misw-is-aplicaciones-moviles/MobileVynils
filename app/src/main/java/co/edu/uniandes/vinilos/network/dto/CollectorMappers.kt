package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.Collector

internal fun CollectorDto.toModel(): Collector =
    Collector(
        id = id ?: missing("collector id"),
        name = name ?: missing("collector name"),
        email = email ?: missing("collector email"),
        albumCount = collectorAlbums.orEmpty().size,
        favoriteCount = favoritePerformers.orEmpty().size,
    )
