package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.models.PerformerDetail
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.network.PerformerServiceAdapter
import kotlinx.coroutines.CompletableDeferred

/** Adapter that returns [performers] or throws [failure], after waiting for [gate] if set, and counts its calls. */
internal class FakePerformerServiceAdapter(
    var performers: List<Performer> = emptyList(),
    var failure: Throwable? = null,
    var gate: CompletableDeferred<Unit>? = null,
) : PerformerServiceAdapter {
    var callCount = 0
        private set

    var detail: PerformerDetail? = null
    val catalogRefreshRequests = mutableListOf<Boolean>()
    val addedAlbumRequests = mutableListOf<Triple<Int, PerformerType, Int>>()
    var addAlbumGate: CompletableDeferred<Unit>? = null

    override suspend fun addAlbum(
        performerId: Int,
        type: PerformerType,
        albumId: Int,
    ) {
        addedAlbumRequests += Triple(performerId, type, albumId)
        addAlbumGate?.await()
        failure?.let { throw it }
    }

    val detailRequests = mutableListOf<Triple<Int, PerformerType, Boolean>>()

    override suspend fun getPerformer(
        id: Int,
        type: PerformerType,
        forceRefresh: Boolean,
    ): PerformerDetail {
        detailRequests += Triple(id, type, forceRefresh)
        gate?.await()
        failure?.let { throw it }
        return checkNotNull(detail) { "Configure the fake performer detail" }
    }

    override suspend fun getPerformers(forceRefresh: Boolean): List<Performer> {
        callCount++
        catalogRefreshRequests += forceRefresh
        gate?.await()
        failure?.let { throw it }
        return performers
    }
}
