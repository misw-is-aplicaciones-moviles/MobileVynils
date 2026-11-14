package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.CollectorDetail
import co.edu.uniandes.vinilos.models.NewCollectorAlbum
import co.edu.uniandes.vinilos.network.CollectorServiceAdapter
import co.edu.uniandes.vinilos.network.dto.CollectorAlbumDto

/** Adapter that returns [collectors] or throws [failure], and counts its calls. */
internal class FakeCollectorServiceAdapter(
    var collectors: List<Collector> = emptyList(),
    var failure: Throwable? = null,
    var detail: CollectorDetail? = null,
    var detailFailure: Throwable? = null,
) : CollectorServiceAdapter {
    var callCount = 0
        private set
    var detailCalls = 0
        private set
    var lastDetailId: Int? = null
        private set
    var lastDetailRefresh: Boolean? = null
        private set

    override suspend fun getCollectors(): List<Collector> {
        callCount++
        failure?.let { throw it }
        return collectors
    }

    override suspend fun getCollector(
        id: Int,
        forceRefresh: Boolean,
    ): CollectorDetail {
        detailCalls++
        lastDetailId = id
        lastDetailRefresh = forceRefresh
        detailFailure?.let { throw it }
        return checkNotNull(detail) { "FakeCollectorServiceAdapter has no detail" }
    }

    override suspend fun addAlbum(
        collectorId: Int,
        albumId: Int,
        draft: NewCollectorAlbum,
    ): CollectorAlbumDto {
        addAlbumCalls++
        lastAddAlbumRequest = Triple(collectorId, albumId, draft)
        return addAlbumResponse
    }

    var addAlbumCalls = 0
        private set
    var lastAddAlbumRequest: Triple<Int, Int, NewCollectorAlbum>? = null
        private set
    var addAlbumResponse: CollectorAlbumDto = CollectorAlbumDto(price = 0, status = "Active", album = null)
}
