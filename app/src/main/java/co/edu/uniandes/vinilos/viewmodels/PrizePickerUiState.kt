package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.Prize
import java.time.LocalDate

data class PrizePickerUiState(
    val prizes: List<Prize> = emptyList(),
    val assignedIds: Set<Int> = emptySet(),
    val selectedId: Int? = null,
    val query: String = "",
    val date: LocalDate? = null,
    val canEdit: Boolean = false,
    val isLoading: Boolean = false,
    val loadError: DataError? = null,
    val isSubmitting: Boolean = false,
    val submitError: DataError? = null,
    val isUncertain: Boolean = false,
    val dateRequired: Boolean = false,
) {
    val selectedPrize: Prize? get() = prizes.firstOrNull { it.id == selectedId && it.id !in assignedIds }
    val visiblePrizes: List<Prize> get() = prizes.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val canContinue: Boolean get() = canEdit && selectedPrize != null && !isSubmitting && !isUncertain
    val canSubmit: Boolean get() = canContinue && date != null
}
