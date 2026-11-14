package co.edu.uniandes.vinilos.viewmodels

sealed interface CollectorAlbumAddEvent {
    data object Added : CollectorAlbumAddEvent
}
