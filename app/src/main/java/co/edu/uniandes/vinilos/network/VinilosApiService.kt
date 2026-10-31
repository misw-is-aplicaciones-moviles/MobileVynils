package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.network.dto.AlbumDto
import co.edu.uniandes.vinilos.network.dto.NewAlbumRequest
import co.edu.uniandes.vinilos.network.dto.NewTrackRequest
import co.edu.uniandes.vinilos.network.dto.TrackDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

internal interface VinilosApiService {
    @GET("albums")
    suspend fun getAlbums(): List<AlbumDto>

    @GET("albums/{id}")
    suspend fun getAlbum(
        @Path("id") id: Int,
        @Header("Cache-Control") cacheControl: String? = null,
    ): Response<AlbumDto>

    @POST("albums")
    suspend fun createAlbum(
        @Body body: NewAlbumRequest,
    ): AlbumDto

    @POST("albums/{id}/tracks")
    suspend fun addTrack(
        @Path("id") albumId: Int,
        @Body body: NewTrackRequest,
    ): TrackDto
}
