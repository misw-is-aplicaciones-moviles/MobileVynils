package co.edu.uniandes.vinilos.database

/**
 * Ids per statement, below the 999 bound variables that SQLite before 3.32 accepts (Android 11 and
 * older). Room does not split list arguments, so a longer list fails with "too many SQL variables" (#139).
 */
internal const val MAX_IDS_PER_STATEMENT = 900

/** Ids of [current] that are not in [kept], split into lists that fit in one statement. */
internal fun staleIdChunks(
    current: List<Int>,
    kept: Collection<Int>,
): List<List<Int>> {
    val keptIds = kept.toHashSet()
    return current.filterNot { it in keptIds }.chunked(MAX_IDS_PER_STATEMENT)
}
