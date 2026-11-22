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

    /**
     * "Crear álbum" found [errors]. Sent on every invalid submit, also when errors were already shown, so the screen
     * moves the focus back to the first text field that still has one (WCAG 3.3.1). Editing a field never sends it.
     */
    data class InvalidSubmit(
        val errors: AlbumFormErrors,
    ) : AlbumCreateEvent
}
