package co.edu.uniandes.vinilos.models

/** A user comment with a rating on an album. [author] is null while the collector who wrote it is unknown. */
data class Comment(
    val id: Int,
    val description: String,
    val rating: Int,
    val author: CommentAuthor? = null,
)
