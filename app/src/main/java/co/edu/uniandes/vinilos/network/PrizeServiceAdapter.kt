package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.ArtistPrize
import co.edu.uniandes.vinilos.models.NewPrize
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.Prize
import co.edu.uniandes.vinilos.network.dto.toCreatedModel
import co.edu.uniandes.vinilos.network.dto.toModel
import co.edu.uniandes.vinilos.network.dto.toPrizeAssignmentRequest
import co.edu.uniandes.vinilos.network.dto.toRequest
import com.google.gson.JsonParseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.time.LocalDate

internal interface PrizeServiceAdapter {
    suspend fun getPrizes(forceRefresh: Boolean = false): List<Prize>

    suspend fun createPrize(draft: NewPrize): Prize

    suspend fun assignPrize(
        performerId: Int,
        performerType: PerformerType,
        prizeId: Int,
        premiationDate: LocalDate,
    ): ArtistPrize
}

internal class PrizeServiceAdapterImpl(
    apiProvider: () -> VinilosApiService,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher,
) : PrizeServiceAdapter {
    private val api by lazy(apiProvider)

    override suspend fun getPrizes(forceRefresh: Boolean): List<Prize> {
        val dtos = withContext(ioDispatcher) { api.getPrizes(if (forceRefresh) "no-cache" else null).requiredBody() }
        return withContext(defaultDispatcher) {
            dtos.map { it?.toModel() ?: throw JsonParseException("Null prize in catalog") }
        }
    }

    override suspend fun createPrize(draft: NewPrize): Prize {
        val request = draft.toRequest()
        val dto = withContext(ioDispatcher) { api.createPrize(request).requiredBody() }
        return withContext(defaultDispatcher) { dto.toCreatedModel(draft) }
    }

    override suspend fun assignPrize(
        performerId: Int,
        performerType: PerformerType,
        prizeId: Int,
        premiationDate: LocalDate,
    ): ArtistPrize {
        val request = premiationDate.toPrizeAssignmentRequest()
        val dto =
            withContext(ioDispatcher) {
                when (performerType) {
                    PerformerType.MUSICIAN -> api.assignPrizeToMusician(prizeId, performerId, request)
                    PerformerType.BAND -> api.assignPrizeToBand(prizeId, performerId, request)
                }.requiredBody()
            }
        return withContext(defaultDispatcher) { dto.toModel(prizeId, performerId, premiationDate) }
    }
}

private fun <T> Response<T>.requiredBody(): T {
    if (!isSuccessful) throw HttpException(this)
    return body() ?: throw JsonParseException("Missing prize response")
}
