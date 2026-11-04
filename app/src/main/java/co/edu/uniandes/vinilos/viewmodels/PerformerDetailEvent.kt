package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.PerformerDetail

sealed interface PerformerDetailEvent {
    data class OpenAlbum(
        val albumId: Int,
    ) : PerformerDetailEvent

    data class OpenMember(
        val performerId: Int,
    ) : PerformerDetailEvent

    data class OpenPrizes(
        val detail: PerformerDetail,
    ) : PerformerDetailEvent

    data object PrizeAssigned : PerformerDetailEvent

    data object OpenRoleSelector : PerformerDetailEvent
}
