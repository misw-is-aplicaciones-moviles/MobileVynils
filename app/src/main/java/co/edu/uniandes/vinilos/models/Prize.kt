package co.edu.uniandes.vinilos.models

/** Prize catalog entry shared by prize creation and assignment. */
data class Prize(
    val id: Int,
    val name: String,
    val description: String,
    val organization: String,
    val assignments: List<PrizeAssignmentRef> = emptyList(),
)
