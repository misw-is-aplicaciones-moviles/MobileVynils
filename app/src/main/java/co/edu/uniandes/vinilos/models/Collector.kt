package co.edu.uniandes.vinilos.models

/**
 * Coleccionista tal como lo necesita el listado. El backend no le da imagen: la pantalla dibuja sus iniciales.
 * [albumCount] y [favoriteCount] cuentan los álbumes de su colección y sus artistas favoritos.
 */
data class Collector(
    val id: Int,
    val name: String,
    val email: String,
    val albumCount: Int,
    val favoriteCount: Int,
)
