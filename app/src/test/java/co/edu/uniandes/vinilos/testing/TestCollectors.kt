package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.models.Collector

/** A collector for the tests; only what a test cares about needs to be given. */
internal fun collector(
    id: Int,
    name: String,
    email: String = "$id@example.com",
    albums: Int = 0,
    favorites: Int = 0,
) = Collector(id = id, name = name, email = email, albumCount = albums, favoriteCount = favorites)
