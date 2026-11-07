package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.BuildConfig

/** El APK de entrega siempre conserva la URL configurada al compilar. */
object BackendEndpoint {
    val baseUrl: String get() = BuildConfig.BASE_URL
}
