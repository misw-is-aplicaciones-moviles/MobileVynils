package co.edu.uniandes.vinilos.models

/** Album association the collector wants to create; the backend assigns its link id. */
data class NewCollectorAlbum(
    val price: Int,
    val status: AlbumStatus,
)
