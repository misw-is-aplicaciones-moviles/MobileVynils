package co.edu.uniandes.vinilos.models

import java.time.LocalDate

/** Confirmed association, including the prize data needed to update the artist detail. */
data class ArtistPrize(
    val id: Int,
    val prize: Prize,
    val premiationDate: LocalDate,
)
