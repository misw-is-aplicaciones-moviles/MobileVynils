package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.network.dto.AlbumDto
import co.edu.uniandes.vinilos.network.dto.BandDto
import co.edu.uniandes.vinilos.network.dto.CollectorAlbumDto
import co.edu.uniandes.vinilos.network.dto.CollectorDto
import co.edu.uniandes.vinilos.network.dto.CommentDto
import co.edu.uniandes.vinilos.network.dto.MusicianDto
import co.edu.uniandes.vinilos.network.dto.NewAlbumRequest
import co.edu.uniandes.vinilos.network.dto.NewCollectorAlbumRequest
import co.edu.uniandes.vinilos.network.dto.NewCommentRequest
import co.edu.uniandes.vinilos.network.dto.NewPrizeRequest
import co.edu.uniandes.vinilos.network.dto.NewTrackRequest
import co.edu.uniandes.vinilos.network.dto.PrizeAssignmentDto
import co.edu.uniandes.vinilos.network.dto.PrizeAssignmentRequest
import co.edu.uniandes.vinilos.network.dto.PrizeDto
import co.edu.uniandes.vinilos.network.dto.TrackDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

// Los POST de favoritos viven en CollectorFavoriteApi: esta interfaz ya está en el tope de detekt.
internal interface VinilosApiService : CollectorFavoriteApi {
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
    suspend fun getAlbums(
        @Header("Cache-Control") cacheControl: String? = null,
    ): List<AlbumDto>

    @Headers("Cache-Control: no-cache")
    @GET("albums")
    suspend fun getAlbumsForAssociation(): Response<List<AlbumDto?>>

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

    @POST("musicians/{performerId}/albums/{albumId}")
    suspend fun addAlbumToMusician(
        @Path("performerId") performerId: Int,
        @Path("albumId") albumId: Int,
    ): Response<AlbumDto>

    @POST("bands/{performerId}/albums/{albumId}")
    suspend fun addAlbumToBand(
        @Path("performerId") performerId: Int,
        @Path("albumId") albumId: Int,
    ): Response<AlbumDto>

    // Responds 200 with the saved comment, its collector and its album.
    @POST("albums/{id}/comments")
    suspend fun addComment(
        @Path("id") albumId: Int,
        @Body body: NewCommentRequest,
    ): CommentDto

    @GET("musicians")
    suspend fun getMusicians(
        @Header("Cache-Control") cacheControl: String? = null,
    ): List<MusicianDto>

    @GET("bands")
    suspend fun getBands(
        @Header("Cache-Control") cacheControl: String? = null,
    ): List<BandDto>

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

    /** Answers the musician with its new band. The backend moves a musician that already had another band. */
    @POST("bands/{bandId}/musicians/{musicianId}")
    suspend fun addMusicianToBand(
        @Path("bandId") bandId: Int,
        @Path("musicianId") musicianId: Int,
    ): Response<MusicianDto>

    @GET("collectors")
    suspend fun getCollectors(): List<CollectorDto>

    @GET("collectors/{id}")
    suspend fun getCollector(
        @Path("id") id: Int,
        @Header("Cache-Control") cacheControl: String? = null,
    ): Response<CollectorDto>

    @GET("collectors/{id}/albums")
    suspend fun getCollectorAlbums(
        @Path("id") id: Int,
        @Header("Cache-Control") cacheControl: String? = null,
    ): Response<List<CollectorAlbumDto?>>

    @POST("collectors/{collectorId}/albums/{albumId}")
    suspend fun addAlbumToCollector(
        @Path("collectorId") collectorId: Int,
        @Path("albumId") albumId: Int,
        @Body body: NewCollectorAlbumRequest,
    ): Response<CollectorAlbumDto>
}

/** POST para asociar un músico o una banda a un coleccionista. El cuerpo va vacío. */
internal interface CollectorFavoriteApi {
    @POST("collectors/{collectorId}/musicians/{musicianId}")
    suspend fun addMusicianToCollector(
        @Path("collectorId") collectorId: Int,
        @Path("musicianId") musicianId: Int,
    ): Response<ResponseBody>

    @POST("collectors/{collectorId}/bands/{bandId}")
    suspend fun addBandToCollector(
        @Path("collectorId") collectorId: Int,
        @Path("bandId") bandId: Int,
    ): Response<ResponseBody>
}
