package co.edu.uniandes.vinilos.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Copia local de un músico o una banda para el listado. Los dos tipos comparten tabla y secuencia de ids en el
 * backend, así que el id basta como clave. [type] guarda el nombre de `PerformerType`; [date] es el nacimiento
 * del músico o la creación de la banda. [bandId] es la banda del músico; su nombre se resuelve al leer.
 */
@Entity(tableName = "performers")
internal data class PerformerEntity(
    @PrimaryKey val id: Int,
    val type: String,
    val name: String,
    val image: String,
    val date: LocalDate,
    val albumCount: Int,
    val memberCount: Int,
    val bandId: Int?,
)
