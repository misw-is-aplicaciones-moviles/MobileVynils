package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.Prize
import java.time.LocalDate

/**
 * [visiblePrizes] and [selectedPrize] are prepared once per change by the ViewModel, off the main thread.
 * The view reads them and does not filter or map the catalog again.
 */
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
    val visiblePrizes: List<PrizePickerRow> = emptyList(),
    val selectedPrize: Prize? = null,
) {
    val canContinue: Boolean get() = canEdit && selectedPrize != null && !isSubmitting && !isUncertain
    val canSubmit: Boolean get() = canContinue && date != null
}

/** One catalog row already marked as assigned or selected. The adapter draws it as-is. */
data class PrizePickerRow(
    val prize: Prize,
    val assigned: Boolean,
    val selected: Boolean,
)

internal data class PreparedPrizeRows(
    val rows: List<PrizePickerRow>,
    val selectedPrize: Prize?,
)

internal data class PrizeCatalogSnapshot(
    val prizes: List<Prize>,
    val assignedIds: Set<Int>,
    val rows: List<PrizePickerRow>,
    val selectedPrize: Prize?,
)

/**
 * Name search, ignoring case. The query is trimmed once. A blank query keeps every prize, in catalog order,
 * with a prize created in HU13 already first. [selectedPrize] ignores the query: the button keeps that name
 * even when the row is filtered out. An assigned prize is never the selection.
 */
internal fun prepareVisiblePrizes(
    prizes: List<Prize>,
    assignedIds: Set<Int>,
    query: String,
    selectedId: Int?,
): PreparedPrizeRows {
    val term = query.trim()
    val rows = ArrayList<PrizePickerRow>(prizes.size)
    var selectedPrize: Prize? = null
    for (prize in prizes) {
        val assigned = prize.id in assignedIds
        val selected = !assigned && prize.id == selectedId
        if (selected) selectedPrize = prize
        if (term.isEmpty() || prize.name.contains(term, ignoreCase = true)) {
            rows += PrizePickerRow(prize, assigned, selected)
        }
    }
    return PreparedPrizeRows(rows, selectedPrize)
}

/** Prize ids whose association id came from the artist that opened the picker. */
internal fun assignedPrizeIds(
    prizes: List<Prize>,
    assignmentIds: Set<Int>,
): Set<Int> {
    if (assignmentIds.isEmpty()) return emptySet()
    val ids = HashSet<Int>()
    for (prize in prizes) {
        if (prize.assignments.any { it.id in assignmentIds }) ids += prize.id
    }
    return ids
}

/** Catalog plus the prize just created, then the rows for [query] and [selectedId]. */
internal fun snapshotPrizeCatalog(
    catalog: List<Prize>,
    created: Prize?,
    assignmentIds: Set<Int>,
    query: String,
    selectedId: Int?,
): PrizeCatalogSnapshot {
    val prizes = if (created == null) catalog else listOf(created) + catalog.filter { it.id != created.id }
    val assignedIds = assignedPrizeIds(prizes, assignmentIds)
    val visible = prepareVisiblePrizes(prizes, assignedIds, query, selectedId)
    return PrizeCatalogSnapshot(prizes, assignedIds, visible.rows, visible.selectedPrize)
}
