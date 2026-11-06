package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.network.dto.AlbumDto
import co.edu.uniandes.vinilos.network.dto.BandDto
import co.edu.uniandes.vinilos.network.dto.CollectorDto
import co.edu.uniandes.vinilos.network.dto.MusicianDto
import co.edu.uniandes.vinilos.network.dto.NewAlbumRequest
import co.edu.uniandes.vinilos.network.dto.NewPrizeRequest
import co.edu.uniandes.vinilos.network.dto.NewTrackRequest
import co.edu.uniandes.vinilos.network.dto.PrizeAssignmentDto
import co.edu.uniandes.vinilos.network.dto.PrizeAssignmentRequest
import co.edu.uniandes.vinilos.network.dto.PrizeDto
import co.edu.uniandes.vinilos.network.dto.TrackDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

internal interface VinilosApiService {
    @GET("prizes")
    suspend fun getPrizes(
        @Header("Cache-Control") cacheControl: String? = null,
    ): Response<List<PrizeDto?>>

    @POST("prizes")
    suspend fun createPrize(
        @Body request: NewPrizeRequest,
    ): Response<PrizeDto>

    @POST("prizes/{prizeId}/musicians/{performerId}")
    suspend fun assignPrizeToMusician(
        @Path("prizeId") prizeId: Int,
        @Path("performerId") performerId: Int,
        @Body request: PrizeAssignmentRequest,
    ): Response<PrizeAssignmentDto>

    @POST("prizes/{prizeId}/bands/{performerId}")
    suspend fun assignPrizeToBand(
        @Path("prizeId") prizeId: Int,
        @Path("performerId") performerId: Int,
        @Body request: PrizeAssignmentRequest,
    ): Response<PrizeAssignmentDto>

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

    @GET("musicians")
    suspend fun getMusicians(): List<MusicianDto>

    @GET("bands")
    suspend fun getBands(): List<BandDto>

    @GET("musicians/{id}")
    suspend fun getMusician(
        @Path("id") id: Int,
        @Header("Cache-Control") cacheControl: String? = null,
    ): Response<MusicianDto>

    @GET("bands/{id}")
    suspend fun getBand(
        @Path("id") id: Int,
        @Header("Cache-Control") cacheControl: String? = null,
    ): Response<BandDto>

    @GET("collectors")
    suspend fun getCollectors(): List<CollectorDto>
}
