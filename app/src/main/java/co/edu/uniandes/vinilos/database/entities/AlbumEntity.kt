package co.edu.uniandes.vinilos.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Copia local de un álbum de BackVynils. El id lo asigna el backend. */
@Entity(tableName = "albums")
internal data class AlbumEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val cover: String,
    val releaseDate: LocalDate,
    val description: String,
    val genre: String,
    val recordLabel: String,
)
