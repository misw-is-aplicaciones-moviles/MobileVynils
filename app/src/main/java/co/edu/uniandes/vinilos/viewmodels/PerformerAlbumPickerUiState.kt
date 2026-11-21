package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.UserRole

/** Immutable selector state; rows are prepared off the main thread, not recomputed by the Fragment. */
data class PerformerAlbumPickerUiState(
    val performerName: String = "",
    val albums: List<Album> = emptyList(),
    val addedAlbumIds: Set<Int> = emptySet(),
    val options: List<PerformerAlbumPickerOption> = emptyList(),
    val query: String = "",
    val selectedAlbumId: Int? = null,
    val phase: AlbumPickerPhase = AlbumPickerPhase.LOADING,
    val role: UserRole? = null,
    val error: DataError? = null,
    val notice: AlbumPickerNotice? = null,
) {
    val isBusy: Boolean
        get() = phase == AlbumPickerPhase.WORKING
    val isLocked: Boolean
        get() = isBusy || phase == AlbumPickerPhase.UNKNOWN_RESULT || phase == AlbumPickerPhase.FINISHED

    // Derived once per immutable state; no lazy delegate shared between Main and Default.
    val selectedAlbum: Album? = albums.firstOrNull { it.id == selectedAlbumId }
    val canConfirm: Boolean
        get() =
            role == UserRole.COLLECTOR && phase == AlbumPickerPhase.CONTENT &&
                selectedAlbum?.let { it.performers.isEmpty() && it.id !in addedAlbumIds } == true
    val canCheck: Boolean
        get() = role == UserRole.COLLECTOR && phase == AlbumPickerPhase.UNKNOWN_RESULT
}

enum class AlbumPickerPhase {
    LOADING,
    CONTENT,
    LOAD_ERROR,
    WORKING,
    UNKNOWN_RESULT,
    FINISHED,
}

enum class AlbumPickerNotice { UNAVAILABLE, NOT_SAVED, CHECK_FAILED, UNCERTAIN, READ_FAILED }

internal fun buildArtistAlbumOptions(state: PerformerAlbumPickerUiState): List<PerformerAlbumPickerOption> {
    val term = state.query.trim()
    val options = ArrayList<PerformerAlbumPickerOption>()
    for (album in state.albums) {
        if (term.isNotEmpty() && !album.name.contains(term, true) &&
            album.performers.none { it.name.contains(term, true) }
        ) {
            continue
        }
        val block =
            when {
                album.id in state.addedAlbumIds -> AlbumPickerBlock.ALREADY_ADDED
                album.performers.isNotEmpty() -> AlbumPickerBlock.ASSIGNED_ELSEWHERE
                else -> null
            }
        options.add(
            PerformerAlbumPickerOption(
                album = album,
                isSelected = album.id == state.selectedAlbumId && block == null,
                block = block,
            ),
        )
    }
    return options
}

data class PerformerAlbumPickerOption(
    val album: Album,
    val isSelected: Boolean,
    val block: AlbumPickerBlock?,
)

enum class AlbumPickerBlock { ALREADY_ADDED, ASSIGNED_ELSEWHERE }
