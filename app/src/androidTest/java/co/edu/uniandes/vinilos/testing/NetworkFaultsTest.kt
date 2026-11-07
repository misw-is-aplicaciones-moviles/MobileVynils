package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.testing.network.NetworkFaults
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy
import java.net.SocketTimeoutException

/** Comprueba el interceptor aisladamente. Los recorridos E2E siguen usando BackVynils real. */
class NetworkFaultsTest {
    @Test
    fun restore_recovers_after_offline_and_timeout() {
        val network = NetworkFaults()
        network.offline()
        assertThrows(IOException::class.java) { network.intercept(chain("/albums")) }
        network.timeout()
        assertThrows(SocketTimeoutException::class.java) { network.intercept(chain("/albums")) }

        network.restore()

        network.intercept(chain("/albums")).use { assertEquals(200, it.code) }
    }

    @Test
    fun fail_next_returns_to_healthy_after_exactly_the_requested_calls() {
        val network = NetworkFaults()
        network.offline()
        network.failNext(1)

        assertThrows(IOException::class.java) { network.intercept(chain("/albums")) }
        network.intercept(chain("/albums")).use { assertEquals(200, it.code) }
        assertThrows(IllegalArgumentException::class.java) { network.failNext(-1) }
    }

    @Test
    fun http_error_is_limited_to_the_selected_path() {
        val network = NetworkFaults()
        network.httpError("/albums", 500)

        network.intercept(chain("/albums/100")).use { assertEquals(500, it.code) }
        network.intercept(chain("/prizes")).use { assertEquals(200, it.code) }
    }

    private fun chain(path: String): Interceptor.Chain {
        val request = Request.Builder().url("http://127.0.0.1$path").build()
        return Proxy.newProxyInstance(
            Interceptor.Chain::class.java.classLoader,
            arrayOf(Interceptor.Chain::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "request" -> {
                    request
                }

                "proceed" -> {
                    Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{}".toResponseBody())
                        .build()
                }

                else -> {
                    error("La prueba no espera ${method.name}")
                }
            }
        } as Interceptor.Chain
    }
}
