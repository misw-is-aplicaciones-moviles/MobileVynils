package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.Album
import com.google.gson.JsonParseException

/** Missing relationships must never turn an occupied album into an eligible, apparently unassigned album. */
internal fun AlbumDto.toAssociationModel(): Album {
    if (id == null || id <= 0 || name.isNullOrBlank()) {
        throw JsonParseException("Missing or invalid association album identity")
    }
    val relationships = performers ?: missing("album performers")
    // Gson can place null elements in this nominally non-null list.
    relationships.forEach { performer: PerformerDto? ->
        if (performer?.id == null || performer.id <= 0 || performer.name.isNullOrBlank()) {
            throw JsonParseException("Missing or invalid album performer")
        }
    }
    return toModel()
}
