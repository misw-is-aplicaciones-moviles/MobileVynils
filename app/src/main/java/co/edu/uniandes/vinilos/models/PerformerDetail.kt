package co.edu.uniandes.vinilos.models

/** Remote artist detail. The list summary remains the Performer shared with HU03. */
data class PerformerDetail(
    val performer: Performer,
    val description: String,
    val albums: List<Album>,
    val members: List<PerformerSummary>,
    val prizeAssignments: List<PrizeAssignmentRef> = emptyList(),
)
