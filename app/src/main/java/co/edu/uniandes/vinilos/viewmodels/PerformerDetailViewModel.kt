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
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.repositories.PerformerRepository
import co.edu.uniandes.vinilos.repositories.SessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PerformerDetailViewModel(
    private val performerId: Int,
    private val performerType: PerformerType?,
    private val repository: PerformerRepository,
    private val session: SessionRepository,
) : ViewModel() {
    private val validIdentity = performerId > 0 && performerType != null
    private val _uiState =
        MediatorLiveData(
            PerformerDetailUiState(
                role = session.role.value,
                isLoading = validIdentity,
                error = if (validIdentity) null else DataError.Serialization,
                hasValidIdentity = validIdentity,
            ),
        ).apply { addSource(session.role) { role -> update { copy(role = role) } } }
    val uiState: LiveData<PerformerDetailUiState> = _uiState
    private val _events = MutableLiveData<Event<PerformerDetailEvent>>()
    val events: LiveData<Event<PerformerDetailEvent>> = _events
    private var loadJob: Job? = null
    private var navigationPending = false

    init {
        load(forceRefresh = false)
    }

    fun retry() = load(forceRefresh = true)

    fun onAlbumClicked(albumId: Int) {
        if (navigationPending || current().detail?.albums?.none { it.id == albumId } != false) return
        navigationPending = true
        _events.value = Event(PerformerDetailEvent.OpenAlbum(albumId))
    }

    fun onMemberClicked(memberId: Int) {
        if (navigationPending || current().detail?.members?.none { it.id == memberId } != false) return
        navigationPending = true
        _events.value = Event(PerformerDetailEvent.OpenMember(memberId))
    }

    fun onNavigationReturned() {
        navigationPending = false
    }

    fun onRoleChipClicked() {
        session.clearRole()
        _events.value = Event(PerformerDetailEvent.OpenRoleSelector)
    }

    private fun load(forceRefresh: Boolean) {
        val type = performerType ?: return
        if (!validIdentity || loadJob?.isActive == true) return
        update { copy(detail = null, isLoading = true, error = null) }
        loadJob =
            viewModelScope.launch {
                when (val result = repository.getPerformer(performerId, type, forceRefresh)) {
                    is DataResult.Success -> update { copy(detail = result.value, isLoading = false) }
                    is DataResult.Failure -> update { copy(error = result.error, isLoading = false) }
                }
            }
    }

    private fun current() = checkNotNull(_uiState.value)

    private fun update(change: PerformerDetailUiState.() -> PerformerDetailUiState) {
        _uiState.value = current().change()
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VinilosApplication
                    val state = createSavedStateHandle()
                    val rawType = state.get<String>(PerformerListViewModel.ARG_PERFORMER_TYPE)
                    PerformerDetailViewModel(
                        state.get<Int>(PerformerListViewModel.ARG_PERFORMER_ID) ?: 0,
                        PerformerType.entries.firstOrNull { it.name == rawType },
                        app.container.performerRepository,
                        app.container.sessionRepository,
                    )
                }
            }
    }
}
