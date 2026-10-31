package co.edu.uniandes.vinilos.models

/** A user comment with a rating on an album. */
data class Comment(
    val id: Int,
    val description: String,
    val rating: Int,
)
