package co.edu.uniandes.vinilos.testing

/** Lee un archivo de `src/test/resources`, por ejemplo `readResource("api/albums.json")`. */
fun readResource(path: String): String {
    val stream =
        requireNotNull(object {}.javaClass.classLoader?.getResourceAsStream(path)) {
            "No existe src/test/resources/$path"
        }
    return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
