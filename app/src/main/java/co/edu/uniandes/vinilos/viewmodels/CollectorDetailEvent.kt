package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.PerformerType

sealed interface CollectorDetailEvent {
    data class OpenAlbumPicker(
        val collectorId: Int,
    ) : CollectorDetailEvent

    data class OpenAlbum(
        val albumId: Int,
    ) : CollectorDetailEvent

    data class OpenFavorite(
        val performerId: Int,
        val type: PerformerType,
    ) : CollectorDetailEvent

    data object OpenRoleSelector : CollectorDetailEvent
}
