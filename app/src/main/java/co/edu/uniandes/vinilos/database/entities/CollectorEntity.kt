package co.edu.uniandes.vinilos.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Copia local de un coleccionista para el listado: solo lo que dibuja la fila. */
@Entity(tableName = "collectors")
internal data class CollectorEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val email: String,
    val albumCount: Int,
    val favoriteCount: Int,
)
