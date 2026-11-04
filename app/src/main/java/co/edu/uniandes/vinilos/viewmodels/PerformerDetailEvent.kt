package co.edu.uniandes.vinilos.viewmodels

sealed interface PerformerDetailEvent {
    data class OpenAlbum(
        val albumId: Int,
    ) : PerformerDetailEvent

    data class OpenMember(
        val performerId: Int,
    ) : PerformerDetailEvent

    data object OpenRoleSelector : PerformerDetailEvent
}
