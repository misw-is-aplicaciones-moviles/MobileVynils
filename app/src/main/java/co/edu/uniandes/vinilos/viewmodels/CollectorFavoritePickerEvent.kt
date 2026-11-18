package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.PerformerType

sealed interface CollectorFavoritePickerEvent {
    data object OpenRoleSelector : CollectorFavoritePickerEvent

    data class FavoriteAdded(
        val collectorId: Int,
        val performerId: Int,
        val type: PerformerType,
        val name: String,
    ) : CollectorFavoritePickerEvent
}
