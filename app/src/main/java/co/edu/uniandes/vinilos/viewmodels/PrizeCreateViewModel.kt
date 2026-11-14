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
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.NewPrize
import co.edu.uniandes.vinilos.models.Prize
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.repositories.PrizeRepository
import co.edu.uniandes.vinilos.repositories.SessionRepository
import kotlinx.coroutines.launch

/**
 * New prize form (HU13). Keeps what the collector typed and the field errors in a [SavedStateHandle], validates on
 * "Crear premio" and sends the prize once.
 *
 * BackVynils does not reject duplicates and the POST is never retried, so a result that is not known (timeout,
 * lost connection, 5xx, unreadable 2xx) locks the form: "Crear premio" turns into a check that reads the catalog and
 * only unlocks sending again when the prize is not there. A prize that is there is returned as created.
 */
class PrizeCreateViewModel(
    private val savedState: SavedStateHandle,
    private val repository: PrizeRepository,
    private val session: SessionRepository,
) : ViewModel() {
    private val _uiState =
        MediatorLiveData(restoredState()).apply {
            addSource(session.role) { role ->
                update { copy(canEdit = role == UserRole.COLLECTOR) }
                if (role != UserRole.COLLECTOR) emit(PrizeCreateEvent.OpenRoleSelector)
            }
        }
    val uiState: LiveData<PrizeCreateUiState> = _uiState

    private val _events = MutableLiveData<Event<PrizeCreateEvent>>()
    val events: LiveData<Event<PrizeCreateEvent>> = _events

    fun onNameChanged(name: String) {
        val state = current()
        if (state.isLocked || state.form.name == name) return
        savedState[NAME] = name
        savedState[NAME_ERROR] = false
        update { copy(form = form.copy(name = name), errors = errors.copy(name = null)) }
    }

    fun onDescriptionChanged(description: String) {
        val state = current()
        if (state.isLocked || state.form.description == description) return
        savedState[DESCRIPTION] = description
        savedState[DESCRIPTION_ERROR] = false
        update { copy(form = form.copy(description = description), errors = errors.copy(description = null)) }
    }

    fun onOrganizationChanged(organization: String) {
        val state = current()
        if (state.isLocked || state.form.organization == organization) return
        savedState[ORGANIZATION] = organization
        savedState[ORGANIZATION_ERROR] = false
        update { copy(form = form.copy(organization = organization), errors = errors.copy(organization = null)) }
    }

    /**
     * "Crear premio". Ignored while a request runs, so a double tap sends one POST. With an unknown result it checks
     * the catalog instead of sending again.
     */
    fun onSubmit() {
        val state = current()
        if (!state.canEdit || session.role.value != UserRole.COLLECTOR || state.isBusy) return
        if (state.isUncertain) {
            check(state.form)
            return
        }
        when (val result = PrizeFormValidator.validate(state.form)) {
            is PrizeFormResult.Invalid -> {
                persistErrors(result.errors)
                update { copy(errors = result.errors, notice = null) }
            }

            is PrizeFormResult.Valid -> {
                send(result.prize)
            }
        }
    }

    /** X, "Cancelar" and Back. Nothing is written; it is ignored while a request runs. */
    fun onCancel() {
        val state = current()
        if (state.isBusy) return
        emit(PrizeCreateEvent.Cancelled(catalogMayBeStale = state.isUncertain))
    }

    fun onRoleChipClicked() {
        if (!current().isBusy) session.clearRole()
    }

    private fun send(prize: NewPrize) {
        // Persisted before the request: if the process dies with the answer unknown, the form restores locked.
        savedState[WRITE_PENDING] = true
        update { copy(errors = PrizeFormErrors(), isSubmitting = true, notice = null) }
        persistErrors(PrizeFormErrors())
        viewModelScope.launch {
            // On success WRITE_PENDING and isSubmitting stay true: the screen closes and must not send again.
            when (val result = repository.createPrize(prize)) {
                is DataResult.Success -> {
                    emit(PrizeCreateEvent.Created(result.value))
                }

                is DataResult.Failure -> {
                    onSendFailed(result.error)
                }
            }
        }
    }

    private fun onSendFailed(error: DataError) {
        val rejected = error is DataError.Http && error.code in CLIENT_ERRORS
        savedState[WRITE_PENDING] = !rejected
        update {
            copy(
                isSubmitting = false,
                isUncertain = !rejected,
                notice = if (rejected) PrizeCreateNotice.REJECTED else PrizeCreateNotice.UNCERTAIN,
            )
        }
    }

    private fun check(form: PrizeForm) {
        val sent = form.trimmed()
        update { copy(isChecking = true, notice = PrizeCreateNotice.UNCERTAIN) }
        viewModelScope.launch {
            when (val result = repository.getPrizes(forceRefresh = true)) {
                is DataResult.Failure -> {
                    update { copy(isChecking = false, notice = PrizeCreateNotice.CHECK_UNAVAILABLE) }
                }

                is DataResult.Success -> {
                    val found = result.value.filter { it.matches(sent) }.maxByOrNull { it.id }
                    if (found != null) {
                        emit(PrizeCreateEvent.Created(found))
                    } else {
                        savedState[WRITE_PENDING] = false
                        update { copy(isChecking = false, isUncertain = false, notice = PrizeCreateNotice.NOT_SAVED) }
                    }
                }
            }
        }
    }

    private fun restoredState(): PrizeCreateUiState {
        val pending = savedState[WRITE_PENDING] ?: false
        return PrizeCreateUiState(
            form =
                PrizeForm(
                    name = savedState[NAME] ?: "",
                    description = savedState[DESCRIPTION] ?: "",
                    organization = savedState[ORGANIZATION] ?: "",
                ),
            errors =
                PrizeFormErrors(
                    name = restoredError(NAME_ERROR),
                    description = restoredError(DESCRIPTION_ERROR),
                    organization = restoredError(ORGANIZATION_ERROR),
                ),
            isUncertain = pending,
            notice = if (pending) PrizeCreateNotice.UNCERTAIN else null,
            canEdit = session.role.value == UserRole.COLLECTOR,
        )
    }

    private fun restoredError(key: String): FieldError? = if (savedState[key] ?: false) FieldError.REQUIRED else null

    private fun persistErrors(errors: PrizeFormErrors) {
        savedState[NAME_ERROR] = errors.name != null
        savedState[DESCRIPTION_ERROR] = errors.description != null
        savedState[ORGANIZATION_ERROR] = errors.organization != null
    }

    private fun current() = checkNotNull(_uiState.value)

    private fun update(change: PrizeCreateUiState.() -> PrizeCreateUiState) {
        _uiState.value = current().change()
    }

    private fun emit(event: PrizeCreateEvent) {
        _events.value = Event(event)
    }

    companion object {
        private const val NAME = "prizeFormName"
        private const val DESCRIPTION = "prizeFormDescription"
        private const val ORGANIZATION = "prizeFormOrganization"
        private const val NAME_ERROR = "prizeFormNameError"
        private const val DESCRIPTION_ERROR = "prizeFormDescriptionError"
        private const val ORGANIZATION_ERROR = "prizeFormOrganizationError"
        private const val WRITE_PENDING = "prizeCreateWritePending"

        // 4xx: the backend refused the request before saving anything. Anything else may have been saved.
        private val CLIENT_ERRORS = 400..499

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    val app =
                        checkNotNull(application as? VinilosApplication) {
                            "PrizeCreateViewModel requires VinilosApplication"
                        }
                    PrizeCreateViewModel(
                        createSavedStateHandle(),
                        app.container.prizeRepository,
                        app.container.sessionRepository,
                    )
                }
            }
    }
}

private fun PrizeForm.trimmed() = PrizeForm(name.trim(), description.trim(), organization.trim())

private fun Prize.matches(form: PrizeForm) =
    name == form.name && description == form.description && organization == form.organization
