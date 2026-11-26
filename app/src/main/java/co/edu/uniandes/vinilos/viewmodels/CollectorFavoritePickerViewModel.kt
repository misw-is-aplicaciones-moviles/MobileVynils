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
import co.edu.uniandes.vinilos.models.CollectorDetail
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.repositories.CollectorRepository
import co.edu.uniandes.vinilos.repositories.PerformerRepository
import co.edu.uniandes.vinilos.repositories.SessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads artists and the collector's favorites, then posts the chosen artist.
 * Visible rows are built in [defaultDispatcher]. A newer search cancels the previous one.
 * One favorite is in flight at a time. Success returns that artist to the detail.
 */
class CollectorFavoritePickerViewModel(
    private val savedState: SavedStateHandle,
    private val performerRepository: PerformerRepository,
    private val collectorRepository: CollectorRepository,
    private val sessionRepository: SessionRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val collectorId: Int = savedState[ARG_COLLECTOR_ID] ?: 0
    private val validIdentity = collectorId > 0
    private val restoredId: Int? = savedState[SELECTED_ID]
    private val restoredType: PerformerType? =
        savedState.get<String>(SELECTED_TYPE)?.let { runCatching { PerformerType.valueOf(it) }.getOrNull() }

    private val _uiState =
        MediatorLiveData(
            CollectorFavoritePickerUiState(
                collectorId = collectorId,
                query = savedState[QUERY] ?: "",
                selectedId = restoredId,
                selectedType = restoredType,
                isLoading = validIdentity && sessionRepository.role.value == UserRole.COLLECTOR,
                error = if (validIdentity) null else DataError.Serialization,
                role = sessionRepository.role.value,
                hasValidIdentity = validIdentity,
            ),
        )
    val uiState: LiveData<CollectorFavoritePickerUiState> = _uiState

    private val _events = MutableLiveData<Event<CollectorFavoritePickerEvent>>()
    val events: LiveData<Event<CollectorFavoritePickerEvent>> = _events
    private var loadJob: Job? = null
    private var filterJob: Job? = null
    private var favoriteKeys: Set<FavoriteKey> = emptySet()

    init {
        _uiState.addSource(sessionRepository.role) { role ->
            val previousRole = current().role
            update { copy(role = role) }
            if (previousRole == UserRole.COLLECTOR && role != UserRole.COLLECTOR) {
                loadJob?.cancel()
                emit(CollectorFavoritePickerEvent.OpenRoleSelector)
            }
        }
        when {
            !validIdentity -> Unit
            sessionRepository.role.value == UserRole.COLLECTOR -> load(forceRefresh = false)
            else -> emit(CollectorFavoritePickerEvent.OpenRoleSelector)
        }
    }

    fun retry() {
        if (current().isSubmitting) return
        load(forceRefresh = true)
    }

    fun onQueryChanged(query: String) {
        if (current().isSubmitting) return
        savedState[QUERY] = query
        update { copy(query = query) }
        refreshVisiblePerformers()
    }

    fun onPerformerSelected(
        id: Int,
        type: PerformerType,
    ) {
        val state = current()
        if (state.isSubmitting || state.role != UserRole.COLLECTOR) return
        if (state.performers.none { it.id == id && it.type == type }) return
        if (isFavorite(id, type, favoriteKeys)) return
        rememberSelection(id, type)
        update { copy(selectedId = id, selectedType = type, submitError = null) }
        refreshVisiblePerformers()
    }

    /** Posts the chosen artist once. A second tap while that call is running does nothing. */
    fun onAddSelected() {
        val performer = current().selectedPerformer
        val type = current().selectedType
        if (!current().canAdd || performer == null || type == null) return
        update { copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch { submitFavorite(performer.id, type, performer.name) }
    }

    fun onRoleChipClicked() {
        if (current().isSubmitting) return
        sessionRepository.clearRole()
        emit(CollectorFavoritePickerEvent.OpenRoleSelector)
    }

    private suspend fun submitFavorite(
        performerId: Int,
        type: PerformerType,
        name: String,
    ) {
        when (val result = collectorRepository.addFavorite(collectorId, performerId, type)) {
            is DataResult.Success -> finishFavorite(performerId, type, name)
            is DataResult.Failure -> update { copy(isSubmitting = false, submitError = result.error) }
        }
    }

    /** A role change while the response is in flight must not report the favorite as added. */
    private fun finishFavorite(
        performerId: Int,
        type: PerformerType,
        name: String,
    ) {
        if (current().role != UserRole.COLLECTOR) return
        emit(
            CollectorFavoritePickerEvent.FavoriteAdded(
                collectorId = collectorId,
                performerId = performerId,
                type = type,
                name = name,
            ),
        )
    }

    private fun load(forceRefresh: Boolean) {
        if (!validIdentity || current().role != UserRole.COLLECTOR || loadJob?.isActive == true) return
        update { copy(isLoading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                val (catalog, detail) = loadCatalogAndCollector(forceRefresh)
                val error = (catalog as? DataResult.Failure)?.error ?: (detail as? DataResult.Failure)?.error
                if (error != null) {
                    update { copy(isLoading = false, error = error, visiblePerformers = emptyList()) }
                    return@launch
                }
                publishLoaded(
                    (catalog as DataResult.Success).value,
                    (detail as DataResult.Success).value,
                )
            }
    }

    /** Catalog and favorites start together, so one slow call does not wait behind the other. */
    private suspend fun loadCatalogAndCollector(forceRefresh: Boolean) =
        coroutineScope {
            val catalog = async { performerRepository.getPerformers(forceRefresh) }
            val collector = async { collectorRepository.getCollector(collectorId, forceRefresh) }
            catalog.await() to collector.await()
        }

    private suspend fun publishLoaded(
        performers: List<Performer>,
        collectorDetail: CollectorDetail,
    ) {
        favoriteKeys = favoriteKeysOf(collectorDetail.favorites)
        val (selectedId, selectedType) = keptSelection(performers, favoriteKeys)
        rememberSelection(selectedId, selectedType)
        filterJob?.cancel()
        val query = current().query
        val (sorted, rows) =
            withContext(defaultDispatcher) {
                val sorted = performers.sortedWith(FAVORITE_PICKER_ORDER)
                sorted to buildVisibleFavorites(sorted, favoriteKeys, query, selectedId, selectedType)
            }
        update {
            copy(
                collectorName = collectorDetail.collector.name,
                performers = sorted,
                selectedId = selectedId,
                selectedType = selectedType,
                visiblePerformers = rows,
                isLoading = false,
                error = null,
            )
        }
        if (current().query != query) refreshVisiblePerformers()
    }

    /** Drops a restored choice that is missing or already a favorite. */
    private fun keptSelection(
        performers: List<Performer>,
        favoriteKeys: Set<FavoriteKey>,
    ): Pair<Int?, PerformerType?> {
        val id = current().selectedId ?: return null to null
        val type = current().selectedType ?: return null to null
        val kept = performers.any { it.id == id && it.type == type } && !isFavorite(id, type, favoriteKeys)
        return if (kept) id to type else null to null
    }

    /** Rebuilds the rows for the current search and selection. Only the latest call publishes. */
    private fun refreshVisiblePerformers() {
        filterJob?.cancel()
        val state = current()
        if (state.isLoading || state.performers.isEmpty()) return
        filterJob =
            viewModelScope.launch {
                val rows =
                    withContext(defaultDispatcher) {
                        buildVisibleFavorites(
                            state.performers,
                            favoriteKeys,
                            state.query,
                            state.selectedId,
                            state.selectedType,
                        )
                    }
                update { copy(visiblePerformers = rows) }
            }
    }

    private fun rememberSelection(
        id: Int?,
        type: PerformerType?,
    ) {
        savedState[SELECTED_ID] = id
        savedState[SELECTED_TYPE] = type?.name
    }

    private fun current(): CollectorFavoritePickerUiState = checkNotNull(_uiState.value)

    private fun update(change: CollectorFavoritePickerUiState.() -> CollectorFavoritePickerUiState) {
        _uiState.value = current().change()
    }

    private fun emit(event: CollectorFavoritePickerEvent) {
        _events.value = Event(event)
    }

    companion object {
        const val ARG_COLLECTOR_ID = "collectorId"
        const val RESULT_FAVORITE_ADDED = "collector_favorite_added"
        const val RESULT_COLLECTOR_ID = "collector_favorite_added_collector_id"
        const val RESULT_PERFORMER_ID = "collector_favorite_added_performer_id"
        const val RESULT_PERFORMER_TYPE = "collector_favorite_added_performer_type"
        const val RESULT_PERFORMER_NAME = "collector_favorite_added_performer_name"
        internal const val QUERY = "collectorFavoritePickerQuery"
        internal const val SELECTED_ID = "collectorFavoritePickerSelectedId"
        internal const val SELECTED_TYPE = "collectorFavoritePickerSelectedType"

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VinilosApplication
                    CollectorFavoritePickerViewModel(
                        createSavedStateHandle(),
                        app.container.performerRepository,
                        app.container.collectorRepository,
                        app.container.sessionRepository,
                    )
                }
            }
    }
}
