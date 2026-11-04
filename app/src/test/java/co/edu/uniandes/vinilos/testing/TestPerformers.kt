package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.models.PerformerType
import java.time.LocalDate

private const val DEFAULT_BIRTH_YEAR = 1947
private const val DEFAULT_FOUNDING_YEAR = 1970

/** A musician for tests. [bandId] and [bandName] set the band it belongs to. */
fun musician(
    id: Int,
    name: String,
    year: Int = DEFAULT_BIRTH_YEAR,
    albums: Int = 0,
    bandId: Int? = null,
    bandName: String? = null,
) = Performer(
    id = id,
    type = PerformerType.MUSICIAN,
    name = name,
    imageUrl = "https://example.com/$id.jpg",
    date = LocalDate.of(year, 1, 1),
    albumCount = albums,
    memberCount = 0,
    bandId = bandId,
    bandName = bandName,
)

/** A band for tests. */
fun band(
    id: Int,
    name: String,
    year: Int = DEFAULT_FOUNDING_YEAR,
    albums: Int = 0,
    members: Int = 0,
) = Performer(
    id = id,
    type = PerformerType.BAND,
    name = name,
    imageUrl = "https://example.com/$id.jpg",
    date = LocalDate.of(year, 1, 1),
    albumCount = albums,
    memberCount = members,
    bandId = null,
    bandName = null,
)
