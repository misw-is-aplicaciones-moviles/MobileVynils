package co.edu.uniandes.vinilos.models

/** An album in a collector's collection, with the price and availability shown on the detail. */
data class CollectorAlbum(
    val albumId: Int,
    val name: String,
    val coverUrl: String,
    val price: Int,
    val status: AlbumStatus,
)
