package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.UserRole

/**
 * Everything the collector screen draws. It starts loading because Room is read before the first list is known.
 * [error] is only set when there is no list to keep showing. There is no `canEdit`: this screen has no action.
 */
data class CollectorListUiState(
    val collectors: List<CollectorItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: DataError? = null,
    val role: UserRole? = null,
) {
    val isEmpty: Boolean get() = !isLoading && error == null && collectors.isEmpty()
}
