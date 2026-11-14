package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.PerformerType

sealed interface AlbumDetailEvent {
    data class OpenPerformer(
        val performerId: Int,
        val type: PerformerType,
    ) : AlbumDetailEvent

    data object OpenRoleSelector : AlbumDetailEvent
}
