package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.Prize

/** One-shot actions of the new prize screen. */
sealed interface PrizeCreateEvent {
    /** The prize exists on the backend: it was just created or a check found it after an uncertain answer. */
    data class Created(
        val prize: Prize,
    ) : PrizeCreateEvent

    /** [catalogMayBeStale] is true when a request with an unknown result was left behind. */
    data class Cancelled(
        val catalogMayBeStale: Boolean,
    ) : PrizeCreateEvent

    data object OpenRoleSelector : PrizeCreateEvent
}
