package co.edu.uniandes.vinilos.network

import okhttp3.OkHttpClient

// release no depende del interceptor ni registra peticiones.
@Suppress("UnusedParameter") // Conserva la misma firma que debug sin configurar registros en release.
internal fun configureNetworkLogging(builder: OkHttpClient.Builder) = Unit
