package co.edu.uniandes.vinilos.models

/** The collector who wrote a comment. `GET /albums/:id` does not include it, so it is resolved apart (HU09). */
data class CommentAuthor(
    val collectorId: Int,
    val name: String,
)
