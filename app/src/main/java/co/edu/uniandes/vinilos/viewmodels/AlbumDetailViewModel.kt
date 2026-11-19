package co.edu.uniandes.vinilos.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Comment
import co.edu.uniandes.vinilos.models.CommentAuthor
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Track
import co.edu.uniandes.vinilos.repositories.AlbumRepository
import co.edu.uniandes.vinilos.repositories.CollectorRepository
import co.edu.uniandes.vinilos.repositories.SessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads a single detail per ViewModel instance and retries only when no request is active.
 * `GET /albums/:id` does not name the author of each comment, so the authors come from
 * [CollectorRepository.getCommentAuthors]: Room for the first frame, and `GET /collectors` only for the comments Room
 * does not know. They never delay or turn the detail into an error: an unknown author shows as "Coleccionista".
 */
class AlbumDetailViewModel(
    private val albumId: Int,
    private val albumRepository: AlbumRepository,
    private val sessionRepository: SessionRepository,
    private val collectorRepository: CollectorRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState =
        MediatorLiveData(AlbumDetailUiState(role = sessionRepository.role.value)).apply {
            addSource(sessionRepository.role) { role -> update { copy(role = role) } }
        }
    val uiState: LiveData<AlbumDetailUiState> = _uiState
    private val _events = MutableLiveData<Event<AlbumDetailEvent>>()
    val events: LiveData<Event<AlbumDetailEvent>> = _events
    private var loadJob: Job? = null
    private var navigationPending = false

    init {
        require(albumId > 0) { "albumId must be positive" }
        load(forceRefresh = false)
    }

    fun retry() {
        load(forceRefresh = true)
    }

    fun onPerformerClicked() {
        val performer =
            _uiState.value
                ?.album
                ?.performers
                ?.firstOrNull() ?: return
        val type = performer.type ?: return
        if (performer.id <= 0 || navigationPending) return
        navigationPending = true
        _events.value = Event(AlbumDetailEvent.OpenPerformer(performer.id, type))
    }

    fun onNavigationReturned() {
        navigationPending = false
    }

    fun onTrackCreated(track: Track) {
        update {
            val currentAlbum = album ?: return@update this
            if (currentAlbum.tracks.any { it.id == track.id }) return@update this
            copy(album = currentAlbum.copy(tracks = currentAlbum.tracks + track))
        }
    }

    /** Adds the comment the sheet published at the end of the list, without another `GET /albums/:id` (HU09). */
    fun onCommentCreated(comment: Comment) {
        update {
            val currentAlbum = album ?: return@update this
            if (currentAlbum.comments.any { it.id == comment.id }) return@update this
            val initials = comment.author?.let { comment.id to collectorInitials(it.name) }
            copy(
                album = currentAlbum.copy(comments = currentAlbum.comments + comment),
                highlightedCommentId = comment.id,
                commentInitials = commentInitials + listOfNotNull(initials),
            )
        }
    }

    fun onRoleChipClicked() {
        sessionRepository.clearRole()
        _events.value = Event(AlbumDetailEvent.OpenRoleSelector)
    }

    private fun load(forceRefresh: Boolean) {
        if (loadJob?.isActive == true) return
        update { copy(album = null, isLoading = true, error = null, commentInitials = emptyMap()) }
        loadJob =
            viewModelScope.launch {
                when (val result = albumRepository.getAlbum(albumId, forceRefresh)) {
                    is DataResult.Success -> {
                        showAlbum(result.value)
                    }

                    is DataResult.Failure -> {
                        update { copy(isLoading = false, error = result.error) }
                    }
                }
            }
    }

    // The first frame waits only for Room, so the names known from earlier visits are there from the start and the
    // backend is never awaited. Only if a comment of this album is not in the index (someone else wrote it) does
    // `GET /collectors` go out, once, after the album is already on screen.
    private suspend fun showAlbum(loaded: Album) {
        val commentIds = loaded.comments.mapTo(HashSet()) { it.id }
        if (commentIds.isEmpty()) {
            update { copy(album = loaded, isLoading = false) }
            return
        }
        val known = collectorRepository.getCommentAuthors(commentIds, revalidate = false).valueOrEmpty()
        val named = withContext(defaultDispatcher) { loaded.withAuthors(known) }
        val initials = withContext(defaultDispatcher) { known.initialsByComment() }
        update { copy(album = named, isLoading = false, commentInitials = initials) }
        revalidateAuthors(commentIds)
    }

    // The repository asks the backend only when an author is missing or Room was not yet checked in this session, and
    // its answer replaces what Room said (a recreated backend reuses comment ids). Otherwise nothing changes on screen.
    private suspend fun revalidateAuthors(commentIds: Set<Int>) {
        val refreshed = collectorRepository.getCommentAuthors(commentIds, revalidate = true).valueOrEmpty()
        val shown = _uiState.value?.album
        if (refreshed.isEmpty() || shown == null) return
        val renamed = withContext(defaultDispatcher) { shown.withAuthors(refreshed, replace = true) }
        if (renamed == shown) return
        val refreshedInitials = withContext(defaultDispatcher) { refreshed.initialsByComment() }
        update {
            // If a comment was published meanwhile, the album changed: name that newer one (a short list) instead.
            val target = if (album === shown) renamed else album?.withAuthors(refreshed, replace = true)
            copy(album = target, commentInitials = commentInitials + refreshedInitials)
        }
    }

    private fun update(change: AlbumDetailUiState.() -> AlbumDetailUiState) {
        _uiState.value = checkNotNull(_uiState.value).change()
    }

    companion object {
        const val ARG_ALBUM_ID = "albumId"

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VinilosApplication
                    val id = checkNotNull(createSavedStateHandle().get<Int>(ARG_ALBUM_ID))
                    AlbumDetailViewModel(
                        id,
                        app.container.albumRepository,
                        app.container.sessionRepository,
                        app.container.collectorRepository,
                    )
                }
            }
    }
}

/**
 * Names each comment with its author in [authors]. A comment that already has one keeps it unless [replace] is true,
 * which is how the backend answer corrects a name Room saved for a reused comment id.
 */
internal fun Album.withAuthors(
    authors: Map<Int, CommentAuthor>,
    replace: Boolean = false,
): Album {
    if (authors.isEmpty()) return this
    return copy(
        comments =
            comments.map { comment ->
                val author = authors[comment.id]
                if (author == null || (comment.author != null && !replace)) comment else comment.copy(author = author)
            },
    )
}

private fun DataResult<Map<Int, CommentAuthor>, DataError>.valueOrEmpty(): Map<Int, CommentAuthor> =
    when (this) {
        is DataResult.Success -> value
        is DataResult.Failure -> emptyMap()
    }

private fun Map<Int, CommentAuthor>.initialsByComment(): Map<Int, String> =
    mapValues { (_, author) -> collectorInitials(author.name) }
