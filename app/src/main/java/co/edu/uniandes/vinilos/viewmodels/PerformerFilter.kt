package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.PerformerType

/** The three chips of the artist list. The selection is applied to the list already loaded. */
enum class PerformerFilter {
    ALL,
    MUSICIANS,
    BANDS,
    ;

    fun accepts(type: PerformerType): Boolean =
        when (this) {
            ALL -> true
            MUSICIANS -> type == PerformerType.MUSICIAN
            BANDS -> type == PerformerType.BAND
        }
}
