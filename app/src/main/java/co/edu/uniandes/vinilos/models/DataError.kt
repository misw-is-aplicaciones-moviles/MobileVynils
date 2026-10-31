package co.edu.uniandes.vinilos.models

/** Errores de datos que la Vista traduce a recursos de texto. */
sealed interface DataError {
    data object Network : DataError

    data class Http(
        val code: Int,
    ) : DataError

    data object Serialization : DataError

    // Reservado para errores identificados explícitamente por otros orígenes de datos.
    data class Unknown(
        val cause: Throwable,
    ) : DataError
}
