package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.network.dto.AlbumDto
import co.edu.uniandes.vinilos.network.dto.NewAlbumRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

internal interface VinilosApiService {
    @GET("albums")
    suspend fun getAlbums(): List<AlbumDto>

    @POST("albums")
    suspend fun createAlbum(
        @Body body: NewAlbumRequest,
    ): AlbumDto
}
