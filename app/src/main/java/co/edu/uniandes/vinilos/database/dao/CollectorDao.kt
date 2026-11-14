package co.edu.uniandes.vinilos.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import co.edu.uniandes.vinilos.database.entities.CollectorEntity

@Dao
internal interface CollectorDao {
    // El id desempata nombres iguales para que el orden sea estable.
    @Query("SELECT * FROM collectors ORDER BY name COLLATE NOCASE, id")
    suspend fun getAll(): List<CollectorEntity>

    @Query("SELECT COUNT(*) FROM collectors")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(collectors: List<CollectorEntity>)

    @Query("DELETE FROM collectors WHERE id NOT IN (:ids)")
    suspend fun deleteAllExcept(ids: List<Int>)

    /** Deja en la tabla exactamente los coleccionistas recibidos, en una sola transacción. */
    @Transaction
    suspend fun replaceAll(collectors: List<CollectorEntity>) {
        deleteAllExcept(collectors.map { it.id })
        upsertAll(collectors)
    }
}
