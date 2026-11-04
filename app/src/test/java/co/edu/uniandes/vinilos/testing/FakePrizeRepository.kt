package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.models.ArtistPrize
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.Prize
import co.edu.uniandes.vinilos.models.PrizeAssignmentRef
import co.edu.uniandes.vinilos.repositories.PrizeRepository
import kotlinx.coroutines.CompletableDeferred
import java.time.LocalDate

internal class FakePrizeRepository : PrizeRepository {
    var catalog: DataResult<List<Prize>, DataError> = DataResult.Success(emptyList())
    var assigned: DataResult<ArtistPrize, DataError> = DataResult.Failure(DataError.Network)
    var loadGate: CompletableDeferred<Unit>? = null
    var saveGate: CompletableDeferred<Unit>? = null
    var loadCalls = 0
    var saveCalls = 0
    var lastForceRefresh = false
    var lastType: PerformerType? = null
    var lastDate: LocalDate? = null

    override suspend fun getPrizes(forceRefresh: Boolean): DataResult<List<Prize>, DataError> {
        loadCalls++
        lastForceRefresh = forceRefresh
        loadGate?.await()
        return catalog
    }

    override suspend fun getArtistPrizes(
        assignments: List<PrizeAssignmentRef>,
        forceRefresh: Boolean,
    ): DataResult<List<ArtistPrize>, DataError> = DataResult.Success(emptyList())

    override suspend fun assignPrize(
        performerId: Int,
        performerType: PerformerType,
        prizeId: Int,
        premiationDate: LocalDate,
    ): DataResult<ArtistPrize, DataError> {
        saveCalls++
        lastType = performerType
        lastDate = premiationDate
        saveGate?.await()
        return assigned
    }
}
