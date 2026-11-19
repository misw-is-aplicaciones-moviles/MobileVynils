package co.edu.uniandes.vinilos.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.repositories.AlbumRepository
import co.edu.uniandes.vinilos.repositories.CollectorRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Form of the "Comentar álbum" sheet (HU09). The collectors come from [CollectorRepository.getCollectors], Room first,
 * and their chips are built once in [defaultDispatcher]. What the collector chose and typed lives in [savedState],
 * so it survives recreation. Errors appear after the first "Publicar comentario" and each one clears when fixed.
 */
class CommentCreateViewModel(
    private val savedState: SavedStateHandle,
    private val albumRepository: AlbumRepository,
    private val collectorRepository: CollectorRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val albumId: Int = savedState[ARG_ALBUM_ID] ?: 0
    private var validationAttempted: Boolean = savedState[VALIDATION_ATTEMPTED] ?: false
    private var collectorsJob: Job? = null

    private val _uiState = MutableLiveData(restoredState())
    val uiState: LiveData<CommentCreateUiState> = _uiState

    private val _events = MutableLiveData<Event<CommentCreateEvent>>()
    val events: LiveData<Event<CommentCreateEvent>> = _events

    init {
        require(albumId > 0) { "albumId must be positive" }
        loadCollectors(forceRefresh = false)
    }

    fun retryCollectors() {
        loadCollectors(forceRefresh = true)
    }

    fun onCollectorSelected(collectorId: Int) {
        val state = current()
        if (state.isSubmitting || state.selectedCollectorId == collectorId) return
        if (state.collectors.none { it.id == collectorId }) return
        savedState[SELECTED_COLLECTOR] = collectorId
        update { copy(selectedCollectorId = collectorId, errors = errors.copy(collector = null), submitError = null) }
    }

    fun onRatingSelected(rating: Int) {
        if (current().isSubmitting || rating !in RATING_RANGE) return
        savedState[RATING] = rating
        update { copy(rating = rating, errors = errors.copy(rating = null), submitError = null) }
    }

    fun onDescriptionChanged(description: String) {
        val state = current()
        if (state.isSubmitting || state.description == description) return
        savedState[DESCRIPTION] = description
        val error = if (validationAttempted && description.isBlank()) CommentFieldError.REQUIRED else null
        update { copy(description = description, errors = errors.copy(description = error), submitError = null) }
    }

    fun onPublish() {
        val state = current()
        if (state.isSubmitting) return
        validationAttempted = true
        savedState[VALIDATION_ATTEMPTED] = true
        when (
            val validation =
                CommentCreateValidator.validate(state.selectedCollectorId, state.rating, state.description)
        ) {
            is CommentCreateValidation.Invalid -> {
                update { copy(errors = validation.errors, submitError = null) }
            }

            is CommentCreateValidation.Valid -> {
                update { copy(errors = CommentCreateErrors(), isSubmitting = true, submitError = null) }
                viewModelScope.launch {
                    when (val result = albumRepository.addComment(albumId, validation.draft)) {
                        is DataResult.Success -> {
                            update { copy(isSubmitting = false) }
                            _events.value = Event(CommentCreateEvent.Created(result.value))
                        }

                        is DataResult.Failure -> {
                            update { copy(isSubmitting = false, submitError = result.error) }
                        }
                    }
                }
            }
        }
    }

    // Room first, so the chips appear at once, then the backend, so a collector created since the last visit is there
    // too (the same cache-then-refresh of the catalog). Without a cache it is a single request. A failed refresh keeps
    // the cached chips; the error only shows when there is nothing to choose from.
    private fun loadCollectors(forceRefresh: Boolean) {
        if (collectorsJob?.isActive == true) return
        update { copy(isLoadingCollectors = true, collectorsError = null) }
        collectorsJob =
            viewModelScope.launch {
                if (!forceRefresh && collectorRepository.hasCachedCollectors()) {
                    val cached = collectorRepository.getCollectors(forceRefresh = false)
                    if (cached is DataResult.Success) showCollectors(cached.value, isFinal = false)
                }
                when (val result = collectorRepository.getCollectors(forceRefresh = true)) {
                    is DataResult.Success -> {
                        showCollectors(result.value, isFinal = true)
                    }

                    is DataResult.Failure -> {
                        val error = if (current().collectors.isEmpty()) result.error else null
                        update { copy(isLoadingCollectors = false, collectorsError = error) }
                    }
                }
            }
    }

    // Only the backend answer can tell that a restored choice no longer exists: the cache may just be older than it.
    private suspend fun showCollectors(
        loaded: List<Collector>,
        isFinal: Boolean,
    ) {
        val options = withContext(defaultDispatcher) { loaded.map { it.toOption() } }
        val restored = current().selectedCollectorId
        val selected = if (isFinal) restored?.takeIf { id -> options.any { it.id == id } } else restored
        if (restored != null && selected == null) savedState[SELECTED_COLLECTOR] = null
        val collectorError = if (validationAttempted && selected == null) CommentFieldError.REQUIRED else null
        update {
            copy(
                // The same list keeps its instance, so the sheet does not rebuild identical chips.
                collectors = if (options == this.collectors) this.collectors else options,
                isLoadingCollectors = !isFinal,
                selectedCollectorId = selected,
                errors = errors.copy(collector = collectorError),
            )
        }
    }

    private fun restoredState(): CommentCreateUiState {
        val description: String = savedState[DESCRIPTION] ?: ""
        val rating: Int = savedState[RATING] ?: CommentCreateValidator.NO_RATING
        val selected: Int? = savedState[SELECTED_COLLECTOR]
        val errors =
            if (validationAttempted) {
                CommentCreateValidator.validate(selected, rating, description).errorsOrNone()
            } else {
                CommentCreateErrors()
            }
        return CommentCreateUiState(
            selectedCollectorId = selected,
            rating = rating,
            description = description,
            errors = errors,
        )
    }

    private fun current(): CommentCreateUiState = checkNotNull(_uiState.value)

    private fun update(change: CommentCreateUiState.() -> CommentCreateUiState) {
        _uiState.value = current().change()
    }

    companion object {
        const val ARG_ALBUM_ID = "albumId"

        private const val SELECTED_COLLECTOR = "commentCollectorId"
        private const val RATING = "commentRating"
        private const val DESCRIPTION = "commentDescription"
        private const val VALIDATION_ATTEMPTED = "commentValidationAttempted"
        private val RATING_RANGE = CommentCreateValidator.MIN_RATING..CommentCreateValidator.MAX_RATING

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VinilosApplication
                    CommentCreateViewModel(
                        createSavedStateHandle(),
                        app.container.albumRepository,
                        app.container.collectorRepository,
                    )
                }
            }
    }
}

private fun Collector.toOption(): CommentCollectorOption =
    CommentCollectorOption(
        id = id,
        name = name,
        initials = collectorInitials(name),
    )

private fun CommentCreateValidation.errorsOrNone(): CommentCreateErrors =
    when (this) {
        is CommentCreateValidation.Invalid -> errors
        is CommentCreateValidation.Valid -> CommentCreateErrors()
    }
