package co.edu.uniandes.vinilos.viewmodels

sealed interface PerformerAlbumPickerEvent {
    data class Associated(
        val albumId: Int,
        val alreadyAssociated: Boolean,
    ) : PerformerAlbumPickerEvent

    data class Closed(
        val refreshRequested: Boolean,
    ) : PerformerAlbumPickerEvent

    data object OpenRoleSelector : PerformerAlbumPickerEvent
}
