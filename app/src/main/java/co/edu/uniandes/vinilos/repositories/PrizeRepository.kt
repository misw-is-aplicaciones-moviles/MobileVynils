package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.models.ArtistPrize
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.NewPrize
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.Prize
import co.edu.uniandes.vinilos.models.PrizeAssignmentRef
import java.time.LocalDate

/** Shared prize data for HU13/HU14; failures are classified at this boundary. */
interface PrizeRepository {
    suspend fun getPrizes(forceRefresh: Boolean = false): DataResult<List<Prize>, DataError>

    /** Resolves artist association IDs with one catalog query, without requests per prize. */
    suspend fun getArtistPrizes(
        assignments: List<PrizeAssignmentRef>,
        forceRefresh: Boolean = false,
    ): DataResult<List<ArtistPrize>, DataError>

    /**
     * HU13. The POST is never retried: the backend does not reject duplicates, so the caller must resolve a
     * lost response (by reading the catalog) before it sends another one.
     */
    suspend fun createPrize(draft: NewPrize): DataResult<Prize, DataError>

    suspend fun assignPrize(
        performerId: Int,
        performerType: PerformerType,
        prizeId: Int,
        premiationDate: LocalDate,
    ): DataResult<ArtistPrize, DataError>
}
