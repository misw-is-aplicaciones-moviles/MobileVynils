package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.UserRole

/**
 * [visibleAlbums] is computed once per change by the ViewModel (off the main thread), so reading it from the view
 * never filters the catalog again.
 */
data class CollectorAlbumPickerUiState(
    val collectorId: Int,
    val collectorName: String = "",
    val albums: List<Album> = emptyList(),
    val addedAlbumIds: Set<Int> = emptySet(),
    val query: String = "",
    val selectedAlbumId: Int? = null,
    val visibleAlbums: List<CollectorAlbumPickerOption> = emptyList(),
    val isLoading: Boolean = true,
    val error: DataError? = null,
    val role: UserRole? = null,
    val hasValidIdentity: Boolean = true,
) {
    /** Searched once per state: [canContinue] and the view reuse it. */
    val selectedAlbum: Album? by lazy(LazyThreadSafetyMode.NONE) {
        albums.firstOrNull { it.id == selectedAlbumId && it.id !in addedAlbumIds }
    }

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

/**
 * The only filtering rule of the picker: [query] matches the album name, performer or genre, ignoring case.
 * A blank query keeps every album and skips the comparisons.
 */
internal fun buildVisibleAlbums(
    albums: List<Album>,
    addedAlbumIds: Set<Int>,
    query: String,
    selectedAlbumId: Int?,
): List<CollectorAlbumPickerOption> {
    val term = query.trim()
    val rows = ArrayList<CollectorAlbumPickerOption>(albums.size)
    for (album in albums) {
        if (term.isNotEmpty() && !album.matches(term)) continue
        rows +=
            CollectorAlbumPickerOption(
                album = album,
                isAlreadyAdded = album.id in addedAlbumIds,
                isSelected = album.id == selectedAlbumId,
            )
    }
    return rows
}

private fun Album.matches(term: String): Boolean =
    name.contains(term, ignoreCase = true) ||
        performerName.orEmpty().contains(term, ignoreCase = true) ||
        genre.apiName.contains(term, ignoreCase = true)
