package co.edu.uniandes.vinilos.models

import java.time.LocalDate

/** The association ID is independent from both the prize ID and the performer ID. */
data class PrizeAssignmentRef(
    val id: Int,
    val premiationDate: LocalDate,
)
