package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.network.dto.AlbumDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

internal interface VinilosApiService {
    @GET("albums")
    suspend fun getAlbums(): List<AlbumDto>

    @GET("albums/{id}")
    suspend fun getAlbum(
        @Path("id") id: Int,
        @Header("Cache-Control") cacheControl: String? = null,
    ): Response<AlbumDto>
}
