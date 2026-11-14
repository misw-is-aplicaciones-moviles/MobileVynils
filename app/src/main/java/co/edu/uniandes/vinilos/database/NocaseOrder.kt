package co.edu.uniandes.vinilos.database

private const val ASCII_CASE_OFFSET = 'a' - 'A'

/**
 * Sort key that orders text like SQLite `COLLATE NOCASE`, which only folds A-Z: "Ángel" goes after "zeta".
 * `lowercase()` would fold Unicode too and give a different order. The repositories use it to return a list in the
 * DAO's order when Room cannot store it and read it back.
 */
internal fun nocaseKey(text: String): String =
    text.map { if (it in 'A'..'Z') it + ASCII_CASE_OFFSET else it }.joinToString("")
