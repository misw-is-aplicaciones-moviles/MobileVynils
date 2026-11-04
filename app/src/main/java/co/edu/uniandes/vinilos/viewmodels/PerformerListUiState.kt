package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.UserRole

/**
 * Everything the artist screen draws. It starts loading because Room is read before the first list is known.
 * [performers] is the loaded list with [filter] applied; [hasPerformers] says whether anything was loaded at all.
 * [error] is only set when there is no list to keep showing. There is no `canEdit`: this screen has no action.
 */
data class PerformerListUiState(
    val performers: List<PerformerItem> = emptyList(),
    val filter: PerformerFilter = PerformerFilter.ALL,
    val hasPerformers: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: DataError? = null,
    val role: UserRole? = null,
) {
    val isEmpty: Boolean get() = !isLoading && error == null && performers.isEmpty()
}
