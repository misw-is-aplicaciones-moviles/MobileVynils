package co.edu.uniandes.vinilos.models

/** Record label of an album, as named by the backend. */
enum class RecordLabel(
    val apiName: String,
) {
    SONY_MUSIC("Sony Music"),
    EMI("EMI"),
    DISCOS_FUENTES("Discos Fuentes"),
    ELEKTRA("Elektra"),
    FANIA_RECORDS("Fania Records"),
}
