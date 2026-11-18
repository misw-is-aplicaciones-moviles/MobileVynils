package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.database.nocaseKey
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.UserRole

/**
 * [visiblePerformers] is built once per change by the ViewModel, off the main thread.
 * The view reads that list and does not filter again.
 */
data class CollectorFavoritePickerUiState(
    val collectorId: Int,
    val collectorName: String = "",
    val performers: List<Performer> = emptyList(),
    val query: String = "",
    val selectedId: Int? = null,
    val selectedType: PerformerType? = null,
    val visiblePerformers: List<CollectorFavoriteOption> = emptyList(),
    val isLoading: Boolean = true,
    val error: DataError? = null,
    val isSubmitting: Boolean = false,
    val submitError: DataError? = null,
    val role: UserRole? = null,
    val hasValidIdentity: Boolean = true,
) {
    /** Searched once per state: [canAdd] and the button label reuse it. */
    val selectedPerformer: Performer? by lazy(LazyThreadSafetyMode.NONE) {
        val id = selectedId ?: return@lazy null
        val type = selectedType ?: return@lazy null
        performers.firstOrNull { performer -> performer.id == id && performer.type == type }
    }

    val canAdd: Boolean
        get() =
            role == UserRole.COLLECTOR &&
                selectedPerformer != null &&
                !isLoading &&
                error == null &&
                !isSubmitting

    val isEmpty: Boolean
        get() = !isLoading && error == null && performers.isEmpty()

    val hasNoSearchResults: Boolean
        get() = !isLoading && error == null && performers.isNotEmpty() && visiblePerformers.isEmpty()
}

data class CollectorFavoriteOption(
    val performer: Performer,
    val isAlreadyFavorite: Boolean,
    val isSelected: Boolean,
)

/** A favorite is an id, and a type when the detail could tell a musician from a band. */
internal data class FavoriteKey(
    val id: Int,
    val type: PerformerType?,
)

internal fun favoriteKeysOf(favorites: List<PerformerSummary>): Set<FavoriteKey> =
    favorites.mapTo(HashSet(favorites.size)) { FavoriteKey(it.id, it.type) }

/**
 * A typed favorite matches that artist only. A favorite without a type matches the id, because musicians and
 * bands share one id sequence and the detail could not tell them apart.
 */
internal fun isFavorite(
    id: Int,
    type: PerformerType,
    keys: Set<FavoriteKey>,
): Boolean = FavoriteKey(id, type) in keys || FavoriteKey(id, null) in keys

/**
 * Name search, ignoring case. A blank query keeps every artist. Rows stay in the artist-list order
 * (`name COLLATE NOCASE`, then id, then type).
 */
internal fun buildVisibleFavorites(
    performers: List<Performer>,
    favoriteKeys: Set<FavoriteKey>,
    query: String,
    selectedId: Int?,
    selectedType: PerformerType?,
): List<CollectorFavoriteOption> {
    val term = query.trim()
    val sorted = performers.sortedWith(compareBy({ nocaseKey(it.name) }, { it.id }, { it.type }))
    val rows = ArrayList<CollectorFavoriteOption>(sorted.size)
    for (performer in sorted) {
        if (term.isNotEmpty() && !performer.name.contains(term, ignoreCase = true)) continue
        val favorite = isFavorite(performer.id, performer.type, favoriteKeys)
        rows +=
            CollectorFavoriteOption(
                performer = performer,
                isAlreadyFavorite = favorite,
                isSelected = !favorite && performer.id == selectedId && performer.type == selectedType,
            )
    }
    return rows
}
