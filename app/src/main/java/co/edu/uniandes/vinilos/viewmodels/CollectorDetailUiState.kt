package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.CollectorDetail
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.UserRole

data class CollectorDetailUiState(
    val detail: CollectorDetail? = null,
    val isLoading: Boolean = true,
    val error: DataError? = null,
    val role: UserRole? = null,
    val hasValidIdentity: Boolean = true,
) {
    /** Collector can see the add actions. They stay disabled until HU10 and HU11 connect them. */
    val canEdit: Boolean
        get() = role == UserRole.COLLECTOR
}
