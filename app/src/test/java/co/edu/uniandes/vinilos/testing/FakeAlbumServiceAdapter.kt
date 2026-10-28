package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.network.AlbumServiceAdapter
import kotlinx.coroutines.CompletableDeferred

/** Adapter that returns [albums] or throws [failure], after waiting for [gate] if set, and counts its calls. */
internal class FakeAlbumServiceAdapter(
    var albums: List<Album> = emptyList(),
    var failure: Throwable? = null,
    var gate: CompletableDeferred<Unit>? = null,
) : AlbumServiceAdapter {
    var callCount = 0
        private set

    override suspend fun getAlbums(): List<Album> {
        callCount++
        gate?.await()
        failure?.let { throw it }
        return albums
    }
}
