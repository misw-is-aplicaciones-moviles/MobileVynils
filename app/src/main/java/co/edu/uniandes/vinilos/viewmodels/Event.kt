package co.edu.uniandes.vinilos.viewmodels

/**
 * Entrega su contenido una sola vez. Un evento de navegación no se repite al rotar la pantalla.
 */
class Event<out T>(
    private val content: T,
) {
    private var handled = false

    fun getContentIfNotHandled(): T? {
        if (handled) {
            return null
        }
        handled = true
        return content
    }
}
