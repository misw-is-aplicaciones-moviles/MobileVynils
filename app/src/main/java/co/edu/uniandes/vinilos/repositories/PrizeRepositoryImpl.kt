package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.models.ArtistPrize
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.Prize
import co.edu.uniandes.vinilos.models.PrizeAssignmentRef
import co.edu.uniandes.vinilos.network.PrizeServiceAdapter
import co.edu.uniandes.vinilos.network.networkRequest
import com.google.gson.JsonParseException
import java.time.LocalDate

internal class PrizeRepositoryImpl(
    private val serviceAdapter: PrizeServiceAdapter,
) : PrizeRepository {
    override suspend fun getPrizes(forceRefresh: Boolean): DataResult<List<Prize>, DataError> =
        networkRequest { serviceAdapter.getPrizes(forceRefresh) }

    override suspend fun getArtistPrizes(
        assignments: List<PrizeAssignmentRef>,
        forceRefresh: Boolean,
    ): DataResult<List<ArtistPrize>, DataError> {
        if (assignments.isEmpty()) return DataResult.Success(emptyList())
        return when (val catalog = getPrizes(forceRefresh)) {
            is DataResult.Failure -> catalog
            is DataResult.Success -> networkRequest { resolveArtistPrizes(assignments, catalog.value) }
        }
    }

    override suspend fun assignPrize(
        performerId: Int,
        performerType: PerformerType,
        prizeId: Int,
        premiationDate: LocalDate,
    ): DataResult<ArtistPrize, DataError> {
        require(performerId > 0) { "performerId must be positive" }
        require(prizeId > 0) { "prizeId must be positive" }
        return networkRequest { serviceAdapter.assignPrize(performerId, performerType, prizeId, premiationDate) }
    }
}

private fun resolveArtistPrizes(
    assignments: List<PrizeAssignmentRef>,
    catalog: List<Prize>,
): List<ArtistPrize> {
    val byAssignment = mutableMapOf<Int, Pair<Prize, LocalDate>>()
    catalog.forEach { prize ->
        prize.assignments.forEach { reference ->
            if (byAssignment.put(reference.id, prize to reference.premiationDate) != null) {
                throw JsonParseException("Duplicate prize assignment id")
            }
        }
    }
    return assignments.map { reference ->
        val entry = byAssignment[reference.id]
        if (entry == null || entry.second != reference.premiationDate) {
            throw JsonParseException("Unresolved or inconsistent artist prize")
        }
        ArtistPrize(reference.id, entry.first, reference.premiationDate)
    }
}
