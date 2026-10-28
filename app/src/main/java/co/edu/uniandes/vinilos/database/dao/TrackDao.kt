package co.edu.uniandes.vinilos.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import co.edu.uniandes.vinilos.database.entities.TrackEntity

@Dao
internal interface TrackDao {
    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY id")
    suspend fun getByAlbum(albumId: Int): List<TrackEntity>

    @Upsert
    suspend fun upsert(track: TrackEntity)

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)
}
