package co.edu.uniandes.vinilos.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.repositories.AlbumRepository
import co.edu.uniandes.vinilos.repositories.PerformerRepository
import co.edu.uniandes.vinilos.repositories.SessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps availability and write verification separate: restoring a pending write never repeats its POST. */
class PerformerAlbumPickerViewModel(
    private val saved: SavedStateHandle,
    private val albums: AlbumRepository,
    private val artists: PerformerRepository,
    private val session: SessionRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val performerId: Int = saved[PerformerAlbumPickerViewModel.PERFORMER_ID] ?: 0
    val performerType =
        PerformerType.entries.firstOrNull {
            it.name == saved.get<String>(PerformerAlbumPickerViewModel.PERFORMER_TYPE)
        }
    private val validIdentity = performerId > 0 && performerType != null
    private val _uiState =
        MediatorLiveData(
            PerformerAlbumPickerUiState(
                query = saved[QUERY] ?: "",
                selectedAlbumId = saved[PENDING] ?: saved[SELECTED],
                role = session.role.value,
                phase = if (validIdentity) AlbumPickerPhase.LOADING else AlbumPickerPhase.LOAD_ERROR,
                error = if (validIdentity) null else DataError.Serialization,
            ),
        )
    val uiState: LiveData<PerformerAlbumPickerUiState> = _uiState
    private val _events = MutableLiveData<Event<PerformerAlbumPickerEvent>>()
    val events: LiveData<Event<PerformerAlbumPickerEvent>> = _events
    private var work: Job? = null
    private var filtering: Job? = null

    init {
        _uiState.addSource(session.role) { role ->
            update { copy(role = role) }
            if (role != UserRole.COLLECTOR) {
                if (!current().isBusy) work?.cancel()
                emit(PerformerAlbumPickerEvent.OpenRoleSelector)
            }
        }
        when {
            session.role.value != UserRole.COLLECTOR -> emit(PerformerAlbumPickerEvent.OpenRoleSelector)
            validIdentity -> retry()
        }
    }

    fun retry() {
        val type = performerType ?: return
        if (!canStart() || !validIdentity) return
        filtering?.cancel()
        update { copy(phase = AlbumPickerPhase.LOADING, error = null, notice = null) }
        work =
            viewModelScope.launch {
                val (catalog, artist) =
                    coroutineScope {
                        val catalog = async { albums.getAlbumsForAssociation() }
                        val artist = async { artists.getPerformer(performerId, type, forceRefresh = true) }
                        catalog.await() to artist.await()
                    }
                when {
                    catalog is DataResult.Failure -> {
                        loadFailed(catalog.error)
                    }

                    artist is DataResult.Failure -> {
                        loadFailed(artist.error)
                    }

                    catalog is DataResult.Success && artist is DataResult.Success -> {
                        val pending = saved.get<Int>(PENDING)
                        val detail = artist.value
                        val added = detail.albums.mapTo(mutableSetOf()) { it.id }
                        val selection =
                            pending ?: current().selectedAlbumId?.takeIf { id ->
                                catalog.value.any { it.id == id && it.performers.isEmpty() } && id !in added
                            }
                        saved[SELECTED] = selection
                        val phase = if (pending == null) AlbumPickerPhase.CONTENT else AlbumPickerPhase.UNKNOWN_RESULT
                        renderOptions(
                            current().copy(
                                performerName = detail.performer.name,
                                albums = catalog.value,
                                addedAlbumIds = added,
                                selectedAlbumId = selection,
                                phase = phase,
                                notice = if (pending == null) null else AlbumPickerNotice.UNCERTAIN,
                            ),
                        )
                    }
                }
            }
    }

    fun onQueryChanged(query: String) {
        if (current().isLocked || current().query == query) return
        saved[QUERY] = query
        update { copy(query = query) }
        refreshOptions()
    }

    fun onAlbumSelected(albumId: Int) {
        val state = current()
        if (state.phase != AlbumPickerPhase.CONTENT || state.role != UserRole.COLLECTOR) return
        if (albumId in state.addedAlbumIds) return
        if (state.albums.none { it.id == albumId && it.performers.isEmpty() }) return
        saved[SELECTED] = albumId
        update { copy(selectedAlbumId = albumId, error = null, notice = null) }
        refreshOptions()
    }

    fun onConfirm() {
        if (!canStart()) return
        if (current().canCheck) {
            checkPending()
        } else if (current().canConfirm) {
            val albumId = current().selectedAlbumId ?: return
            filtering?.cancel()
            update { copy(phase = AlbumPickerPhase.WORKING, notice = null, error = null) }
            work = viewModelScope.launch { preflight(albumId) }
        }
    }

    fun onClose() {
        if (!current().isBusy) emit(PerformerAlbumPickerEvent.Closed(saved.get<Int>(PENDING) != null))
    }

    fun onRoleChipClicked() {
        if (!current().isBusy) session.clearRole()
    }

    private suspend fun preflight(albumId: Int) {
        when (val result = albums.getAlbumForAssociation(albumId)) {
            is DataResult.Failure -> {
                update {
                    copy(phase = AlbumPickerPhase.CONTENT, notice = AlbumPickerNotice.READ_FAILED, error = result.error)
                }
            }

            is DataResult.Success -> {
                val album = result.value
                val belongs = belongsToDestination(album)
                when {
                    belongs == true -> finish(albumId, alreadyAssociated = true)
                    belongs == null -> membershipUnknown()
                    album.performers.isNotEmpty() -> unavailable(album)
                    session.role.value == UserRole.COLLECTOR -> send(albumId)
                }
            }
        }
    }

    private suspend fun send(albumId: Int) {
        val type = performerType ?: return
        saved[PENDING] = albumId
        artists.addAlbum(performerId, type, albumId)
        // Success and transport failures both need a read. Nothing here resends a write.
        verify(albumId)
    }

    private fun checkPending() {
        val albumId = saved.get<Int>(PENDING) ?: return
        update { copy(phase = AlbumPickerPhase.WORKING, notice = null) }
        work = viewModelScope.launch { verify(albumId) }
    }

    private suspend fun verify(albumId: Int) {
        when (val result = albums.getAlbumForAssociation(albumId)) {
            is DataResult.Failure -> {
                update {
                    copy(
                        phase = AlbumPickerPhase.UNKNOWN_RESULT,
                        notice = AlbumPickerNotice.CHECK_FAILED,
                        error = result.error,
                    )
                }
            }

            is DataResult.Success -> {
                val album = result.value
                val belongs = belongsToDestination(album)
                when {
                    belongs == true -> {
                        finish(albumId, alreadyAssociated = false)
                    }

                    belongs == null -> {
                        membershipUnknown()
                    }

                    album.performers.isNotEmpty() -> {
                        unavailable(album)
                    }

                    else -> {
                        saved.remove<Int>(PENDING)
                        renderOptions(
                            current().copy(
                                albums = replaceAlbum(album),
                                phase = AlbumPickerPhase.CONTENT,
                                notice = AlbumPickerNotice.NOT_SAVED,
                                error = null,
                            ),
                        )
                    }
                }
            }
        }
    }

    private suspend fun belongsToDestination(album: Album): Boolean? {
        val type = performerType ?: return false
        if (album.performers.any { it.id == performerId && it.type == type }) return true
        if (album.performers.none { it.id == performerId && it.type == null }) return false
        // The backend can omit the subtype in nested performers; confirm using the typed detail endpoint.
        val result = artists.getPerformer(performerId, type, forceRefresh = true)
        return when (result) {
            is DataResult.Success -> result.value.albums.any { it.id == album.id }
            is DataResult.Failure -> null
        }
    }

    private fun membershipUnknown() {
        val phase = if (saved.get<Int>(PENDING) == null) AlbumPickerPhase.CONTENT else AlbumPickerPhase.UNKNOWN_RESULT
        update {
            copy(
                phase = phase,
                notice = AlbumPickerNotice.READ_FAILED,
            )
        }
    }

    private suspend fun unavailable(album: Album) {
        saved.remove<Int>(PENDING)
        saved.remove<Int>(SELECTED)
        renderOptions(
            current().copy(
                albums = replaceAlbum(album),
                selectedAlbumId = null,
                phase = AlbumPickerPhase.CONTENT,
                notice = AlbumPickerNotice.UNAVAILABLE,
                error = null,
            ),
        )
    }

    private fun finish(
        albumId: Int,
        alreadyAssociated: Boolean,
    ) {
        // Retain the pending marker until the result leaves the screen: restoring must never resend.
        saved[PENDING] = albumId
        update { copy(phase = AlbumPickerPhase.FINISHED) }
        if (session.role.value == UserRole.COLLECTOR) {
            emit(PerformerAlbumPickerEvent.Associated(albumId, alreadyAssociated))
        }
    }

    private fun replaceAlbum(album: Album): List<Album> = current().albums.map { if (it.id == album.id) album else it }

    private fun canStart(): Boolean = session.role.value == UserRole.COLLECTOR && work?.isActive != true

    private fun loadFailed(error: DataError) = update { copy(phase = AlbumPickerPhase.LOAD_ERROR, error = error) }

    private fun refreshOptions() {
        filtering?.cancel()
        val state = current()
        filtering =
            viewModelScope.launch {
                val options = withContext(defaultDispatcher) { buildArtistAlbumOptions(state) }
                if (current() == state) update { copy(options = options) }
            }
    }

    private suspend fun renderOptions(state: PerformerAlbumPickerUiState) {
        val options = withContext(defaultDispatcher) { buildArtistAlbumOptions(state) }
        _uiState.value = state.copy(options = options, role = session.role.value)
    }

    private fun current() = checkNotNull(_uiState.value)

    private fun update(change: PerformerAlbumPickerUiState.() -> PerformerAlbumPickerUiState) {
        _uiState.value = current().change()
    }

    private fun emit(event: PerformerAlbumPickerEvent) {
        _events.value = Event(event)
    }

    companion object {
        const val PERFORMER_ID = "performerId"
        const val PERFORMER_TYPE = "performerType"
        const val ALBUM_ID = "associatedAlbumId"
        const val ALBUM_ASSOCIATED = "performer_album_associated"
        const val ALREADY_ASSOCIATED = "albumWasAlreadyAssociated"
        const val REFRESH_REQUESTED = "performer_albums_refresh_requested"

        private const val QUERY = "artistAlbumQuery"
        private const val SELECTED = "artistAlbumSelected"
        private const val PENDING = "artistAlbumWritePending"

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VinilosApplication
                    PerformerAlbumPickerViewModel(
                        createSavedStateHandle(),
                        app.container.albumRepository,
                        app.container.performerRepository,
                        app.container.sessionRepository,
                    )
                }
            }
    }
}
