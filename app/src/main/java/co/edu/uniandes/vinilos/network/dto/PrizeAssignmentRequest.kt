package co.edu.uniandes.vinilos.network.dto

import java.time.LocalDate

/** Exactly the one field accepted by the backend; the date cannot be omitted. */
internal data class PrizeAssignmentRequest(
    val premiationDate: String,
)

internal fun LocalDate.toPrizeAssignmentRequest(): PrizeAssignmentRequest =
    PrizeAssignmentRequest(premiationDate = "${this}T00:00:00.000Z")
