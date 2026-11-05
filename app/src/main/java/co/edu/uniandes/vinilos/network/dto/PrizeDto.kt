package co.edu.uniandes.vinilos.network.dto

internal data class PrizeDto(
    val id: Int? = null,
    val name: String? = null,
    val description: String? = null,
    val organization: String? = null,
    val performerPrizes: List<PrizeAssignmentRefDto?>? = null,
)

internal data class PrizeAssignmentRefDto(
    val id: Int? = null,
    val premiationDate: String? = null,
)

internal data class PrizeAssignmentDto(
    val id: Int? = null,
    val premiationDate: String? = null,
    val prize: PrizeDto? = null,
    val performer: PrizePerformerDto? = null,
)

internal data class PrizePerformerDto(
    val id: Int? = null,
)
