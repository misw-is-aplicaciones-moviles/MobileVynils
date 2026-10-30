package co.edu.uniandes.vinilos.testing

import android.database.sqlite.SQLiteException
import co.edu.uniandes.vinilos.database.dao.TrackDao
import co.edu.uniandes.vinilos.database.entities.TrackEntity

/** In-memory TrackDao for Repository tests. */
internal class FakeTrackDao(
    initial: List<TrackEntity> = emptyList(),
) : TrackDao {
    private val rows = initial.associateBy { it.id }.toMutableMap()

    var writeCount = 0
        private set

    var writeFailure: SQLiteException? = null

    override suspend fun getByAlbum(albumId: Int): List<TrackEntity> = rows.values.filter { it.albumId == albumId }

    override suspend fun upsert(track: TrackEntity) {
        writeCount++
        writeFailure?.let { throw it }
        rows[track.id] = track
    }

    override suspend fun upsertAll(tracks: List<TrackEntity>) {
        writeCount++
        writeFailure?.let { throw it }
        tracks.forEach { rows[it.id] = it }
    }
}
