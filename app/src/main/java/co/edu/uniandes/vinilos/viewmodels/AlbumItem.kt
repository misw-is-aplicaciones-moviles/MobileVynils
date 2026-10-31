package co.edu.uniandes.vinilos.viewmodels

/** What one catalog row needs; the adapter only joins these values with string resources. */
data class AlbumItem(
    val id: Int,
    val position: String,
    val name: String,
    val artistName: String?,
    val genreName: String,
    val year: Int,
    val coverUrl: String,
)
