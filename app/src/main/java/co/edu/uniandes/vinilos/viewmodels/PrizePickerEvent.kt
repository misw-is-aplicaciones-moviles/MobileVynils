package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.ArtistPrize

sealed interface PrizePickerEvent {
    data object OpenDate : PrizePickerEvent

    data object OpenCreate : PrizePickerEvent

    data object PrizeCreated : PrizePickerEvent

    data object Close : PrizePickerEvent

    data object OpenRoleSelector : PrizePickerEvent

    data object ReviewArtist : PrizePickerEvent

    data class Assigned(
        val assignment: ArtistPrize,
    ) : PrizePickerEvent
}
