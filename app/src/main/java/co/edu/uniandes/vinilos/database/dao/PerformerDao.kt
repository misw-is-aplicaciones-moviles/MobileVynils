package co.edu.uniandes.vinilos.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import co.edu.uniandes.vinilos.database.entities.PerformerEntity
import co.edu.uniandes.vinilos.database.staleIdChunks

@Dao
internal interface PerformerDao {
    // El id desempata nombres iguales para que el orden sea estable.
    @Query("SELECT * FROM performers ORDER BY name COLLATE NOCASE, id")
    suspend fun getAll(): List<PerformerEntity>

    @Query("SELECT COUNT(*) FROM performers")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(performers: List<PerformerEntity>)

    @Query("SELECT id FROM performers")
    suspend fun getIds(): List<Int>

    // Recibe a lo sumo MAX_IDS_PER_STATEMENT ids: Room crea una variable por id (#139).
    @Query("DELETE FROM performers WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    /** Pone al músico en la banda si aún no estaba en ella; devuelve cuántas filas cambió (0 o 1). */
    @Query(
        "UPDATE performers SET bandId = :bandId " +
            "WHERE id = :musicianId AND type = 'MUSICIAN' AND (bandId IS NULL OR bandId != :bandId)",
    )
    suspend fun setMusicianBand(
        musicianId: Int,
        bandId: Int,
    ): Int

    @Query("UPDATE performers SET memberCount = memberCount + 1 WHERE id = :bandId AND type = 'BAND'")
    suspend fun incrementMemberCount(bandId: Int)

    /**
     * Refleja en la caché un integrante nuevo sin volver a descargar la lista. Solo suma al conteo de la
     * banda si el músico cambió de verdad, así que aplicarlo dos veces no cuenta dos integrantes.
     */
    @Transaction
    suspend fun applyMembership(
        musicianId: Int,
        bandId: Int,
    ) {
        if (setMusicianBand(musicianId, bandId) > 0) incrementMemberCount(bandId)
    }

    /** Deja en la tabla exactamente los artistas recibidos, en una sola transacción. */
    @Transaction
    suspend fun replaceAll(performers: List<PerformerEntity>) {
        staleIdChunks(getIds(), performers.map { it.id }).forEach { deleteByIds(it) }
        upsertAll(performers)
    }
}
