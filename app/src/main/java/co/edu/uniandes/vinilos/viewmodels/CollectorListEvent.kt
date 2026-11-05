package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.DataError

/** One-shot actions of the collector screen. */
sealed interface CollectorListEvent {
    data class OpenDetail(
        val collectorId: Int,
    ) : CollectorListEvent

    data class ShowRefreshError(
        val error: DataError,
    ) : CollectorListEvent

    data object OpenRoleSelector : CollectorListEvent
}
