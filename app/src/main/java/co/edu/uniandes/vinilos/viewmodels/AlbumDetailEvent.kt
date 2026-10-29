package co.edu.uniandes.vinilos.viewmodels

sealed interface AlbumDetailEvent {
    data object OpenRoleSelector : AlbumDetailEvent
}
