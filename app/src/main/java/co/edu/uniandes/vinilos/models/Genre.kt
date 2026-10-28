package co.edu.uniandes.vinilos.models

/** Music genre of an album, as named by the backend. */
enum class Genre(
    val apiName: String,
) {
    CLASSICAL("Classical"),
    SALSA("Salsa"),
    ROCK("Rock"),
    FOLK("Folk"),
    UNKNOWN("Unknown"),
    ;

    companion object {
        /** Returns the genre whose [apiName] matches exactly, or [UNKNOWN] for any other value. */
        fun fromApiName(name: String?): Genre = entries.firstOrNull { it.apiName == name } ?: UNKNOWN
    }
}
