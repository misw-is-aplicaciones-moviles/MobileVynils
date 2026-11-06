package co.edu.uniandes.vinilos.viewmodels

sealed interface CollectorAlbumPickerEvent {
    data class ContinueWithAlbum(
        val collectorId: Int,
        val albumId: Int,
        val albumName: String,
    ) : CollectorAlbumPickerEvent

    data object OpenRoleSelector : CollectorAlbumPickerEvent
}
