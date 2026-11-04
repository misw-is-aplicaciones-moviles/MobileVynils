package co.edu.uniandes.vinilos.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import co.edu.uniandes.vinilos.database.entities.PerformerEntity

@Dao
internal interface PerformerDao {
    // El id desempata nombres iguales para que el orden sea estable.
    @Query("SELECT * FROM performers ORDER BY name COLLATE NOCASE, id")
    suspend fun getAll(): List<PerformerEntity>

    @Query("SELECT COUNT(*) FROM performers")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(performers: List<PerformerEntity>)

    @Query("DELETE FROM performers WHERE id NOT IN (:ids)")
    suspend fun deleteAllExcept(ids: List<Int>)

    /** Deja en la tabla exactamente los artistas recibidos, en una sola transacción. */
    @Transaction
    suspend fun replaceAll(performers: List<PerformerEntity>) {
        deleteAllExcept(performers.map { it.id })
        upsertAll(performers)
    }
}
