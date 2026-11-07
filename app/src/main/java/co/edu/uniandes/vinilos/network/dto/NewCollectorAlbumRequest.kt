package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.NewCollectorAlbum

/** The BackVynils collector-album endpoint accepts exactly these two fields. */
internal data class NewCollectorAlbumRequest(
    val price: Int,
    val status: String,
)

internal fun NewCollectorAlbum.toRequest(): NewCollectorAlbumRequest =
    NewCollectorAlbumRequest(
        price = price,
        status = status.apiName,
    )
