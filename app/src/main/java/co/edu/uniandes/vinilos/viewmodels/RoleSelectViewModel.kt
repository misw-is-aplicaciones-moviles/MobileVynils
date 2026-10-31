package co.edu.uniandes.vinilos.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.repositories.SessionRepository

/**
 * El selector no carga datos: guarda el rol y pide abrir el catálogo.
 */
class RoleSelectViewModel(
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val _events = MutableLiveData<Event<RoleSelectEvent>>()
    val events: LiveData<Event<RoleSelectEvent>> = _events

    fun onRoleSelected(role: UserRole) {
        sessionRepository.selectRole(role)
        _events.value = Event(RoleSelectEvent.OpenCatalog)
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    val app =
                        checkNotNull(application as? VinilosApplication) {
                            "RoleSelectViewModel requiere VinilosApplication"
                        }
                    RoleSelectViewModel(app.container.sessionRepository)
                }
            }
    }
}

/** El catálogo es el único destino al salir del selector. */
sealed interface RoleSelectEvent {
    data object OpenCatalog : RoleSelectEvent
}
