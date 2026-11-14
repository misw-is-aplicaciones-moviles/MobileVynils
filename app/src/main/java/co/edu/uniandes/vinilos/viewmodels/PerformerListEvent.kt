package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.PerformerType

/** One-shot actions of the artist screen. */
sealed interface PerformerListEvent {
    data class OpenDetail(
        val performerId: Int,
        val type: PerformerType,
    ) : PerformerListEvent

    data class ShowRefreshError(
        val error: DataError,
    ) : PerformerListEvent

    data object OpenRoleSelector : PerformerListEvent
}
