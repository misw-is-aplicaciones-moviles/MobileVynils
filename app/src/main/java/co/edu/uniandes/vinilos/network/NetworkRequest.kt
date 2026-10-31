package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import retrofit2.HttpException
import java.io.EOFException
import java.io.IOException

/**
 * Convierte errores remotos conocidos cuando lo invoca un Repository.
 * La cancelación y los errores de programación se propagan; la caché se maneja en el Repository.
 */
internal suspend fun <T> networkRequest(block: suspend () -> T): DataResult<T, DataError> {
    currentCoroutineContext().ensureActive()
    return try {
        DataResult.Success(block())
    } catch (error: HttpException) {
        currentCoroutineContext().ensureActive()
        DataResult.Failure(DataError.Http(error.code()))
    } catch (_: JsonParseException) {
        currentCoroutineContext().ensureActive()
        DataResult.Failure(DataError.Serialization)
    } catch (_: MalformedJsonException) {
        currentCoroutineContext().ensureActive()
        DataResult.Failure(DataError.Serialization)
    } catch (_: EOFException) {
        currentCoroutineContext().ensureActive()
        DataResult.Failure(DataError.Serialization)
    } catch (_: IOException) {
        currentCoroutineContext().ensureActive()
        DataResult.Failure(DataError.Network)
    }
}
