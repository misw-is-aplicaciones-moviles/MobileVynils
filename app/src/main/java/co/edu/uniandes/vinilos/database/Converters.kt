package co.edu.uniandes.vinilos.database

import androidx.room.TypeConverter
import java.time.LocalDate

/** Fechas como texto ISO-8601 (`2024-01-31`). java.time llega a API 21 por desugaring. */
internal class Converters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate): String = date.toString()

    @TypeConverter
    fun toLocalDate(value: String): LocalDate = LocalDate.parse(value)
}
