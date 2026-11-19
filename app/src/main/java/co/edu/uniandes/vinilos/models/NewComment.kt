package co.edu.uniandes.vinilos.models

/** A comment ready to publish: [rating] is already in range and [description] is trimmed and not blank. */
data class NewComment(
    val description: String,
    val rating: Int,
    val collectorId: Int,
)
