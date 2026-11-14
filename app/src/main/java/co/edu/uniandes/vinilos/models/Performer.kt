package co.edu.uniandes.vinilos.models

import java.time.LocalDate

/**
 * Artista tal como lo necesita el listado: un músico o una banda.
 * [date] es el nacimiento del músico o la creación de la banda. [memberCount] solo cuenta en las bandas.
 * [bandId] y [bandName] son la banda a la que pertenece un músico, derivada de `GET /bands`.
 */
data class Performer(
    val id: Int,
    val type: PerformerType,
    val name: String,
    val imageUrl: String,
    val date: LocalDate,
    val albumCount: Int,
    val memberCount: Int,
    val bandId: Int?,
    val bandName: String?,
)
