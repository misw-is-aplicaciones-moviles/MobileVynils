package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.Performer

/**
 * [visibleRows] is computed once per change by the ViewModel (off the main thread), so reading it from the view
 * never filters the musicians again. [conflict] is set when the chosen musician already belongs to another band.
 */
data class BandMemberPickerUiState(
    val bandId: Int,
    val bandName: String,
    val musicians: List<Performer> = emptyList(),
    val memberIds: Set<Int> = emptySet(),
    val query: String = "",
    val selectedId: Int? = null,
    val visibleRows: List<BandMemberOption> = emptyList(),
    val canEdit: Boolean = false,
    val isLoading: Boolean = false,
    val loadError: DataError? = null,
    val isSubmitting: Boolean = false,
    val submitError: DataError? = null,
    val isUncertain: Boolean = false,
    val conflict: BandConflict? = null,
) {
    /** Searched once per state: [canAdd] and the view reuse it. */
    val selectedMusician: Performer? by lazy(LazyThreadSafetyMode.NONE) {
        musicians.firstOrNull { it.id == selectedId && it.id !in memberIds }
    }

    val canAdd: Boolean
        get() = canEdit && selectedMusician != null && !isLoading && !isSubmitting && !isUncertain

    val isEmpty: Boolean
        get() = !isLoading && loadError == null && musicians.isEmpty()

    val hasNoSearchResults: Boolean
        get() = !isLoading && loadError == null && musicians.isNotEmpty() && visibleRows.isEmpty()
}

/** One row of the picker. [otherBandName] is set when the musician belongs to a band other than this one. */
data class BandMemberOption(
    val musician: Performer,
    val isMember: Boolean,
    val isSelected: Boolean,
    val otherBandName: String?,
)

/** A musician that already has a band; [bandName] is null when that band is not in the list. */
data class BandConflict(
    val musicianName: String,
    val bandName: String?,
)

/**
 * The only filtering rule of the picker: [query] matches the musician name, ignoring case and surrounding spaces.
 * A blank query keeps every musician and skips the comparisons.
 */
internal fun buildBandMemberOptions(
    musicians: List<Performer>,
    memberIds: Set<Int>,
    bandId: Int,
    query: String,
    selectedId: Int?,
): List<BandMemberOption> {
    val term = query.trim()
    val rows = ArrayList<BandMemberOption>(musicians.size)
    for (musician in musicians) {
        if (term.isNotEmpty() && !musician.name.contains(term, ignoreCase = true)) continue
        val isMember = musician.id in memberIds
        val hasOtherBand = !isMember && musician.hasOtherBand(bandId)
        rows +=
            BandMemberOption(
                musician = musician,
                isMember = isMember,
                isSelected = !isMember && musician.id == selectedId,
                otherBandName = if (hasOtherBand) musician.bandName else null,
            )
    }
    return rows
}

/** A musician can belong to one band only; the backend does not check it and would move the musician instead. */
internal fun Performer.hasOtherBand(bandId: Int): Boolean = this.bandId != null && this.bandId != bandId
