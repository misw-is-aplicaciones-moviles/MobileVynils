package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.UserRole

data class CollectorAlbumPickerUiState(
    val collectorId: Int,
    val collectorName: String = "",
    val albums: List<Album> = emptyList(),
    val addedAlbumIds: Set<Int> = emptySet(),
    val query: String = "",
    val selectedAlbumId: Int? = null,
    val isLoading: Boolean = true,
    val error: DataError? = null,
    val role: UserRole? = null,
    val hasValidIdentity: Boolean = true,
) {
    val visibleAlbums: List<CollectorAlbumPickerOption>
        get() {
            val term = query.trim()
            return albums
                .asSequence()
                .filter { album ->
                    term.isEmpty() ||
                        album.name.contains(term, ignoreCase = true) ||
                        album.performerName.orEmpty().contains(term, ignoreCase = true) ||
                        album.genre.apiName.contains(term, ignoreCase = true)
                }.map { album ->
                    CollectorAlbumPickerOption(
                        album = album,
                        isAlreadyAdded = album.id in addedAlbumIds,
                        isSelected = album.id == selectedAlbumId,
                    )
                }.toList()
        }

    val selectedAlbum: Album?
        get() = albums.firstOrNull { it.id == selectedAlbumId && it.id !in addedAlbumIds }

    val canContinue: Boolean
        get() = role == UserRole.COLLECTOR && selectedAlbum != null && !isLoading && error == null

    val isEmpty: Boolean
        get() = !isLoading && error == null && albums.isEmpty()

    val hasNoSearchResults: Boolean
        get() = !isLoading && error == null && albums.isNotEmpty() && visibleAlbums.isEmpty()
}

data class CollectorAlbumPickerOption(
    val album: Album,
    val isAlreadyAdded: Boolean,
    val isSelected: Boolean,
)
