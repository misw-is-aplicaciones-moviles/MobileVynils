package co.edu.uniandes.vinilos.models

/** Resultado de una operación de datos, independiente de Android y de sus fuentes. */
sealed interface DataResult<out T, out E> {
    data class Success<T>(
        val value: T,
    ) : DataResult<T, Nothing>

    data class Failure<E>(
        val error: E,
    ) : DataResult<Nothing, E>
}
