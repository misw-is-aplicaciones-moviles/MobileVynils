package co.edu.uniandes.vinilos.network

import android.net.TrafficStats
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/** Cliente compartido por los Service Adapters. Su propietario controla su ciclo de vida. */
internal class NetworkClient(
    baseUrl: String,
    cacheDirectory: File,
    connectTimeoutMillis: Long = CONNECT_TIMEOUT_MILLIS,
    readTimeoutMillis: Long = READ_TIMEOUT_MILLIS,
) : Closeable {
    internal val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            // Evita repetir un POST cuando el servidor lo guardó pero se perdió la respuesta.
            .retryOnConnectionFailure(false)
            // Sin reintentos, una conexión que el servidor ya cerró falla la petición. Node cierra las inactivas a los
            // 5 s (keepAliveTimeout), así que el pool las descarta antes y nunca reutiliza una cerrada.
            .connectionPool(ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_MILLIS, TimeUnit.MILLISECONDS))
            .cache(Cache(cacheDirectory, HTTP_CACHE_BYTES))
            // Etiqueta el tráfico de la app: el Network Profiler lo atribuye y StrictMode deja de reportar
            // UntaggedSocketViolation en debug.
            .socketFactory(TaggedSocketFactory())
            .apply { configureNetworkLogging(this) }
            .build()

    private val retrofit =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(JsonResponseConverterFactory())
            .addConverterFactory(
                GsonConverterFactory.create(GsonBuilder().setStrictness(Strictness.STRICT).create()),
            ).build()

    /** Crea una interfaz Retrofit; los DTO y mappers los define cada historia. */
    fun <T> create(service: Class<T>): T = retrofit.create(service)

    // Al cerrar, no deben quedar peticiones en curso. En producción vive tanto como AppContainer.
    override fun close() {
        httpClient.cache?.close()
        httpClient.connectionPool.evictAll()
        httpClient.dispatcher.executorService.shutdown()
    }

    /**
     * Pone el tag de [TrafficStats] en el hilo que crea cada socket. OkHttp crea y conecta los sockets en sus
     * propios hilos, que es donde StrictMode revisa el tag; por eso no basta con etiquetar el hilo que llama.
     */
    private class TaggedSocketFactory(
        private val delegate: SocketFactory = getDefault(),
    ) : SocketFactory() {
        override fun createSocket(): Socket = tagged { delegate.createSocket() }

        override fun createSocket(
            host: String?,
            port: Int,
        ): Socket = tagged { delegate.createSocket(host, port) }

        override fun createSocket(
            host: String?,
            port: Int,
            localHost: InetAddress?,
            localPort: Int,
        ): Socket = tagged { delegate.createSocket(host, port, localHost, localPort) }

        override fun createSocket(
            host: InetAddress?,
            port: Int,
        ): Socket = tagged { delegate.createSocket(host, port) }

        override fun createSocket(
            address: InetAddress?,
            port: Int,
            localAddress: InetAddress?,
            localPort: Int,
        ): Socket = tagged { delegate.createSocket(address, port, localAddress, localPort) }

        private inline fun tagged(create: () -> Socket): Socket {
            TrafficStats.setThreadStatsTag(TRAFFIC_TAG)
            return create()
        }
    }

    private companion object {
        /** Valor arbitrario que identifica el tráfico de Vinilos en las herramientas de red. */
        const val TRAFFIC_TAG = 0x5649_4E4C
        const val CONNECT_TIMEOUT_MILLIS = 10_000L
        const val READ_TIMEOUT_MILLIS = 30_000L
        const val HTTP_CACHE_BYTES = 10L * 1024 * 1024
        const val MAX_IDLE_CONNECTIONS = 5
        const val KEEP_ALIVE_MILLIS = 4_000L
    }
}
