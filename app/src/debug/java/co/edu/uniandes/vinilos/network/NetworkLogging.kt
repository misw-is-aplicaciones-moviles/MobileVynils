package co.edu.uniandes.vinilos.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

internal fun configureNetworkLogging(builder: OkHttpClient.Builder) {
    builder.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
}
