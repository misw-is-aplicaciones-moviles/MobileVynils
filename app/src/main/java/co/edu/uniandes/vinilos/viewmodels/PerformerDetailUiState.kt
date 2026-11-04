package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.PerformerDetail
import co.edu.uniandes.vinilos.models.UserRole

data class PerformerDetailUiState(
    val detail: PerformerDetail? = null,
    val isLoading: Boolean = true,
    val error: DataError? = null,
    val role: UserRole? = null,
    val hasValidIdentity: Boolean = true,
)
