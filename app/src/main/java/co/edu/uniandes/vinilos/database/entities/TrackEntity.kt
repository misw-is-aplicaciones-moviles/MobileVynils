package co.edu.uniandes.vinilos.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Canción de un álbum. Se borra junto con su álbum. */
@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("albumId")],
)
internal data class TrackEntity(
    @PrimaryKey val id: Int,
    val name: String,
    // Formato "mm:ss", igual que el backend.
    val duration: String,
    val albumId: Int,
)
