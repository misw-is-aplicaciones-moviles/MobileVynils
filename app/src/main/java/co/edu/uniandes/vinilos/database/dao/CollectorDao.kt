package co.edu.uniandes.vinilos.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import co.edu.uniandes.vinilos.database.entities.CollectorEntity
import co.edu.uniandes.vinilos.database.staleIdChunks

@Dao
internal interface CollectorDao {
    // El id desempata nombres iguales para que el orden sea estable.
    @Query("SELECT * FROM collectors ORDER BY name COLLATE NOCASE, id")
    suspend fun getAll(): List<CollectorEntity>

    @Query("SELECT COUNT(*) FROM collectors")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(collectors: List<CollectorEntity>)

    @Query("SELECT id FROM collectors")
    suspend fun getIds(): List<Int>

    // Recibe a lo sumo MAX_IDS_PER_STATEMENT ids: Room crea una variable por id (#139).
    @Query("DELETE FROM collectors WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    /** Deja en la tabla exactamente los coleccionistas recibidos, en una sola transacción. */
    @Transaction
    suspend fun replaceAll(collectors: List<CollectorEntity>) {
        staleIdChunks(getIds(), collectors.map { it.id }).forEach { deleteByIds(it) }
        upsertAll(collectors)
    }
}
