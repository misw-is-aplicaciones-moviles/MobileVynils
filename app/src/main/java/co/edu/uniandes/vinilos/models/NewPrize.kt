package co.edu.uniandes.vinilos.models

/** Prize the collector wants to create; the backend assigns its id. */
data class NewPrize(
    val name: String,
    val description: String,
    val organization: String,
)
