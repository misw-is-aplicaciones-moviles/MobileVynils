package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.DataError

/** One-shot actions of the catalog screen. */
sealed interface AlbumListEvent {
    data class OpenDetail(
        val albumId: Int,
    ) : AlbumListEvent

    data class ShowRefreshError(
        val error: DataError,
    ) : AlbumListEvent

    data object ShowCreatePending : AlbumListEvent

    data object OpenRoleSelector : AlbumListEvent
}
