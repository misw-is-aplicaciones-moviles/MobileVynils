package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.BuildConfig

/** Solo el runner de debug instala una URL efímera antes de crear el cliente HTTP. */
object BackendEndpoint {
    @Volatile private var testUrl: String? = null

    val baseUrl: String get() = testUrl ?: BuildConfig.BASE_URL

    fun useIsolatedTests() {
        testUrl = "http://127.0.0.1:31370/"
    }
}
