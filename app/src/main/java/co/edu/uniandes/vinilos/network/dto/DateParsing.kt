package co.edu.uniandes.vinilos.network.dto

import com.google.gson.JsonParseException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Reads a backend ISO 8601 instant as a date in UTC: the backend sends midnight UTC,
 * which is the previous day in UTC-5. [field] names the value in the error.
 */
internal fun parseUtcDate(
    text: String,
    field: String,
): LocalDate =
    try {
        Instant.parse(text).atOffset(ZoneOffset.UTC).toLocalDate()
    } catch (error: DateTimeParseException) {
        throw JsonParseException("Invalid $field: $text", error)
    }

internal fun missing(field: String): Nothing = throw JsonParseException("Missing required field: $field")
