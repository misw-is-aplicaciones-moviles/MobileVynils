package co.edu.uniandes.vinilos.core.ui.navigation

import kotlinx.serialization.Serializable

// Rutas tipadas de Navigation Compose (docs/10-diseno-de-la-aplicacion.md, sección 3).
// Cada historia añade aquí sus rutas; ningún otro archivo declara rutas.

@Serializable
data object AlbumList

@Serializable
data object ArtistList

@Serializable
data object CollectorList
