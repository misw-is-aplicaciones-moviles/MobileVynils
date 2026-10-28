package co.edu.uniandes.vinilos.network

import com.google.gson.JsonParseException
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/** Normaliza los errores de estructura JSON solo dentro de la conversión de la respuesta. */
internal class JsonResponseConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *> {
        val delegate = retrofit.nextResponseBodyConverter<Any>(this, type, annotations)
        return Converter<ResponseBody, Any> { body ->
            try {
                delegate.convert(body)
            } catch (error: IllegalStateException) {
                // Gson puede lanzar IllegalStateException si un token no coincide con el tipo esperado.
                throw JsonParseException("La estructura JSON no coincide con el tipo de respuesta", error)
            }
        }
    }
}
