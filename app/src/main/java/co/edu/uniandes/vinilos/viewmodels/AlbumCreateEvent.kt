package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.DataError

/** One-shot actions of the new album screen. */
sealed interface AlbumCreateEvent {
    data object Created : AlbumCreateEvent

    data object Cancelled : AlbumCreateEvent

    data class Failed(
        val error: DataError,
    ) : AlbumCreateEvent

    data object OpenRoleSelector : AlbumCreateEvent
}
