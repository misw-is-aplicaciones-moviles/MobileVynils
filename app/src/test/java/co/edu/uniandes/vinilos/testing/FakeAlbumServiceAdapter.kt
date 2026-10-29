package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.NewAlbum
import co.edu.uniandes.vinilos.models.NewTrack
import co.edu.uniandes.vinilos.models.Track
import co.edu.uniandes.vinilos.network.AlbumServiceAdapter
import kotlinx.coroutines.CompletableDeferred

/**
 * Adapter that returns [albums] or throws [failure], after waiting for [gate] if set, and counts its calls.
 * [createAlbum] returns [createdAlbum] or throws [failure], and records every draft in [createdDrafts].
 */
internal class FakeAlbumServiceAdapter(
    var albums: List<Album> = emptyList(),
    var failure: Throwable? = null,
    var gate: CompletableDeferred<Unit>? = null,
    var createdAlbum: Album? = null,
    var createdTrack: Track? = null,
) : AlbumServiceAdapter {
    var callCount = 0
        private set
    var detailResult: Album? = null
    val detailRequests = mutableListOf<Pair<Int, Boolean>>()

    override suspend fun getAlbum(
        id: Int,
        forceRefresh: Boolean,
    ): Album {
        detailRequests.add(id to forceRefresh)
        gate?.await()
        failure?.let { throw it }
        return checkNotNull(detailResult ?: albums.firstOrNull { it.id == id }) {
            "No detail configured for album $id"
        }
    }

    val createdDrafts = mutableListOf<NewAlbum>()

    override suspend fun getAlbums(): List<Album> {
        callCount++
        gate?.await()
        failure?.let { throw it }
        return albums
    }

    override suspend fun createAlbum(draft: NewAlbum): Album {
        createdDrafts += draft
        failure?.let { throw it }
        return checkNotNull(createdAlbum) { "Set createdAlbum before calling createAlbum" }
    }

    val addedTrackRequests = mutableListOf<Pair<Int, NewTrack>>()

    override suspend fun addTrack(
        albumId: Int,
        draft: NewTrack,
    ): Track {
        addedTrackRequests += albumId to draft
        failure?.let { throw it }
        return checkNotNull(createdTrack) { "Set createdTrack before calling addTrack" }
    }
}
