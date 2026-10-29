package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.network.dto.AlbumDto
import retrofit2.http.GET

internal interface VinilosApiService {
    @GET("albums")
    suspend fun getAlbums(): List<AlbumDto>
}
