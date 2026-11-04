package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.ArtistPrize
import co.edu.uniandes.vinilos.models.Prize
import co.edu.uniandes.vinilos.models.PrizeAssignmentRef
import com.google.gson.JsonParseException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

internal fun PrizeDto.toModel(): Prize =
    Prize(
        id = id.validPrizeId("prize id"),
        name = name ?: missingPrizeField("name"),
        description = description ?: missingPrizeField("description"),
        organization = organization ?: missingPrizeField("organization"),
        assignments = performerPrizes.orEmpty().map { it?.toModel() ?: missingPrizeField("assignment") },
    )

internal fun PrizeAssignmentDto.toModel(
    expectedPrizeId: Int,
    expectedPerformerId: Int,
    expectedDate: LocalDate,
): ArtistPrize {
    val assignedPrize = prize?.toModel() ?: missingPrizeField("prize")
    val performerId = performer?.id.validPrizeId("performer id")
    val date = parsePremiationDate(premiationDate)
    if (assignedPrize.id != expectedPrizeId || performerId != expectedPerformerId || date != expectedDate) {
        throw JsonParseException("Prize assignment does not match the request")
    }
    return ArtistPrize(id.validPrizeId("assignment id"), assignedPrize, date)
}

internal fun PrizeAssignmentRefDto.toModel(): PrizeAssignmentRef =
    PrizeAssignmentRef(
        id = id.validPrizeId("assignment id"),
        premiationDate = parsePremiationDate(premiationDate),
    )

private fun parsePremiationDate(text: String?): LocalDate =
    try {
        Instant.parse(text ?: missingPrizeField("premiationDate")).atOffset(ZoneOffset.UTC).toLocalDate()
    } catch (error: DateTimeParseException) {
        throw JsonParseException("Invalid premiationDate", error)
    }

private fun Int?.validPrizeId(field: String): Int =
    this?.takeIf { it > 0 } ?: throw JsonParseException("Invalid $field")

private fun missingPrizeField(field: String): Nothing = throw JsonParseException("Missing prize field: $field")
