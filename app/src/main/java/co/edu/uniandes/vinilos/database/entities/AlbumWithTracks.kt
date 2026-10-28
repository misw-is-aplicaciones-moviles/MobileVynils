package co.edu.uniandes.vinilos.database.entities

import androidx.room.Embedded
import androidx.room.Relation

/** Álbum con sus canciones para el detalle (HU02). */
internal data class AlbumWithTracks(
    @Embedded val album: AlbumEntity,
    @Relation(parentColumn = "id", entityColumn = "albumId")
    val tracks: List<TrackEntity>,
)
