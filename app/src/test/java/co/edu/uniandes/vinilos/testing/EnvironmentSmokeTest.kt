package co.edu.uniandes.vinilos.testing

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.GET
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR

/** Prueba de humo del entorno (TR-05) y ejemplo de uso de las utilidades de `testing`. */
class EnvironmentSmokeTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val mockWebServerRule = MockWebServerRule()

    private val api: SmokeApi by lazy {
        Retrofit
            .Builder()
            .baseUrl(mockWebServerRule.baseUrl)
            .build()
            .create()
    }

    @Test
    fun `getOrAwaitValue returns value when posted from main coroutine`() =
        runTest {
            val liveData = MutableLiveData<String>()

            launch(Dispatchers.Main) { liveData.value = "Buscando América" }
            advanceUntilIdle()

            assertEquals("Buscando América", liveData.getOrAwaitValue())
        }

    @Test
    fun `retrofit receives fixture when server returns albums`() =
        runTest {
            mockWebServerRule.enqueueJson("api/albums.json")

            val body = api.getAlbums().string()

            assertEquals(readResource("api/albums.json"), body)
            assertEquals("/albums", mockWebServerRule.server.takeRequest().path)
        }

    @Test
    fun `retrofit throws HttpException when server returns 500`() =
        runTest {
            mockWebServerRule.server.enqueue(MockResponse().setResponseCode(HTTP_INTERNAL_ERROR))

            try {
                api.getAlbums()
                fail("Se esperaba HttpException")
            } catch (e: HttpException) {
                assertEquals(HTTP_INTERNAL_ERROR, e.code())
            }
        }
}

/** Sin convertidor JSON a propósito: el convertidor y el servicio real los define TR-02. */
private interface SmokeApi {
    @GET("albums")
    suspend fun getAlbums(): ResponseBody
}
