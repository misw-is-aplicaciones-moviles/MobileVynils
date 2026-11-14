package co.edu.uniandes.vinilos.models

/**
 * Remote collector detail. [collector] is the list summary; its counts come from [favorites] and [albums].
 * Reading this detail does not update the list cache.
 */
data class CollectorDetail(
    val collector: Collector,
    val telephone: String,
    val favorites: List<PerformerSummary>,
    val albums: List<CollectorAlbum>,
)
