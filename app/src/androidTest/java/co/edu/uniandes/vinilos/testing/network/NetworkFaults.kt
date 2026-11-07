package co.edu.uniandes.vinilos.testing.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Interceptor de aplicación que simula fallos de red en el cliente OkHttp de la app.
 * Reemplaza a BackendFirewall y al selector de proxy de VinilosTestRunner.
 *
 * Solo afecta al cliente de la app; BackendFixtures usa su propio cliente y sigue funcionando.
 * Sin APIs de Java 8 (getAndUpdate, etc.): corre en API 21.
 */
class NetworkFaults : Interceptor {
    private sealed class Mode {
        object Healthy : Mode()

        object Offline : Mode()

        object Timeout : Mode()

        class HttpError(
            val pathPrefix: String,
            val code: Int,
        ) : Mode()
    }

    private val lock = Any()
    private var mode: Mode = Mode.Healthy
    private var failuresLeft = 0

    /** Cada llamada falla con IOException hasta restore(). */
    fun offline() = set(Mode.Offline)

    /** Cada llamada falla con SocketTimeoutException hasta restore(). */
    fun timeout() = set(Mode.Timeout)

    /** Las siguientes [count] llamadas fallan con IOException; después, red sana. */
    fun failNext(count: Int) =
        synchronized(lock) {
            require(count >= 0)
            mode = Mode.Healthy
            failuresLeft = count
        }

    /** Respuesta [code] sintetizada para las rutas que empiezan por [pathPrefix], p. ej. "/albums". */
    fun httpError(
        pathPrefix: String,
        code: Int,
    ) = set(Mode.HttpError(pathPrefix, code))

    fun restore() =
        synchronized(lock) {
            mode = Mode.Healthy
            failuresLeft = 0
        }

    private fun set(newMode: Mode) =
        synchronized(lock) {
            mode = newMode
            failuresLeft = 0
        }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val current: Mode
        val failThisOne: Boolean
        synchronized(lock) {
            current = mode
            failThisOne = failuresLeft > 0
            if (failThisOne) failuresLeft--
        }

        if (failThisOne) throw IOException("Fallo de red simulado (failNext): ${request.url}")

        return when (current) {
            Mode.Healthy -> {
                chain.proceed(request)
            }

            Mode.Offline -> {
                throw IOException("Red simulada sin conexión: ${request.url}")
            }

            Mode.Timeout -> {
                throw SocketTimeoutException("Timeout simulado: ${request.url}")
            }

            is Mode.HttpError -> {
                if (request.url.encodedPath.startsWith(current.pathPrefix)) {
                    Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(current.code)
                        .message("Simulated ${current.code}")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    chain.proceed(request)
                }
            }
        }
    }
}
