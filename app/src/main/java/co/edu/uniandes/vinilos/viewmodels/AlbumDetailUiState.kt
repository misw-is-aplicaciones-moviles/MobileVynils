package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.UserRole

/** Complete album detail and the session role used by the shared header. */
data class AlbumDetailUiState(
    val album: Album? = null,
    val isLoading: Boolean = true,
    val error: DataError? = null,
    val role: UserRole? = null,
)
