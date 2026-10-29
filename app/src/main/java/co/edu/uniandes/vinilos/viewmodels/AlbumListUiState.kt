package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.UserRole

/**
 * Everything the catalog screen draws. It starts loading because Room is read before the first list is known.
 * [error] is only set when there is no list to keep showing.
 */
data class AlbumListUiState(
    val albums: List<AlbumItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: DataError? = null,
    val role: UserRole? = null,
) {
    val canEdit: Boolean get() = role == UserRole.COLLECTOR
    val isEmpty: Boolean get() = !isLoading && error == null && albums.isEmpty()
}
