package co.edu.uniandes.vinilos.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import co.edu.uniandes.vinilos.database.entities.AlbumEntity
import co.edu.uniandes.vinilos.database.entities.AlbumWithTracks

// Upsert y no Insert(REPLACE): REPLACE borra la fila y el CASCADE se llevaría sus canciones.
@Dao
internal interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<AlbumEntity>

    @Transaction
    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getWithTracks(id: Int): AlbumWithTracks?

    @Upsert
    suspend fun upsert(album: AlbumEntity)

    @Upsert
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums WHERE id NOT IN (:ids)")
    suspend fun deleteAllExcept(ids: List<Int>)

    /** Deja en la tabla exactamente los álbumes recibidos, sin perder las canciones de los que siguen. */
    @Transaction
    suspend fun replaceAll(albums: List<AlbumEntity>) {
        deleteAllExcept(albums.map { it.id })
        upsertAll(albums)
    }
}
