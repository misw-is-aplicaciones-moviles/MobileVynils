package co.edu.uniandes.vinilos.network

import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.Closeable
import java.io.File
import java.util.concurrent.TimeUnit

/** Cliente compartido por los Service Adapters. Su propietario controla su ciclo de vida. */
internal class NetworkClient(
    baseUrl: String,
    cacheDirectory: File,
    connectTimeoutMillis: Long = CONNECT_TIMEOUT_MILLIS,
    readTimeoutMillis: Long = READ_TIMEOUT_MILLIS,
) : Closeable {
    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            // Evita repetir un POST cuando el servidor lo guardó pero se perdió la respuesta.
            .retryOnConnectionFailure(false)
            .cache(Cache(cacheDirectory, HTTP_CACHE_BYTES))
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

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000L
        const val READ_TIMEOUT_MILLIS = 30_000L
        const val HTTP_CACHE_BYTES = 10L * 1024 * 1024
    }
}
