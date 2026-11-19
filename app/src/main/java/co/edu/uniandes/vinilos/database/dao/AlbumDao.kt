package co.edu.uniandes.vinilos.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import co.edu.uniandes.vinilos.database.entities.AlbumEntity
import co.edu.uniandes.vinilos.database.entities.AlbumWithTracks
import co.edu.uniandes.vinilos.database.staleIdChunks

// Upsert y no Insert(REPLACE): REPLACE borra la fila y el CASCADE se llevaría sus canciones.
@Dao
internal interface AlbumDao {
    @Query("SELECT EXISTS(SELECT 1 FROM albums)")
    suspend fun hasAlbums(): Boolean

    // El id desempata nombres iguales: el repositorio reproduce este orden si Room no puede guardar la lista.
    @Query("SELECT * FROM albums ORDER BY name COLLATE NOCASE, id")
    suspend fun getAll(): List<AlbumEntity>

    @Transaction
    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getWithTracks(id: Int): AlbumWithTracks?

    @Upsert
    suspend fun upsert(album: AlbumEntity)

    @Upsert
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("SELECT id FROM albums")
    suspend fun getIds(): List<Int>

    // Recibe a lo sumo MAX_IDS_PER_STATEMENT ids: Room crea una variable por id (#139).
    @Query("DELETE FROM albums WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    /** Deja en la tabla exactamente los álbumes recibidos, sin perder las canciones de los que siguen. */
    @Transaction
    suspend fun replaceAll(albums: List<AlbumEntity>) {
        staleIdChunks(getIds(), albums.map { it.id }).forEach { deleteByIds(it) }
        upsertAll(albums)
    }
}
