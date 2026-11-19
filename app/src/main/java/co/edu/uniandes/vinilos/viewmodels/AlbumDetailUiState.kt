package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.UserRole

/**
 * Complete album detail and the session role used by the shared header. [highlightedCommentId] is the comment just
 * published from this screen, drawn with the darker border of the mockup.
 */
data class AlbumDetailUiState(
    val album: Album? = null,
    val isLoading: Boolean = true,
    val error: DataError? = null,
    val role: UserRole? = null,
    val highlightedCommentId: Int? = null,
    // Initials of each named comment by comment id, computed off the main thread so a bind only copies them.
    val commentInitials: Map<Int, String> = emptyMap(),
) {
    val canEdit: Boolean get() = album != null && role == UserRole.COLLECTOR
}
