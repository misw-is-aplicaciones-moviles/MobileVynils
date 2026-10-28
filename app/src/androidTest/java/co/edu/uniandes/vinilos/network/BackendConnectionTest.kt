package co.edu.uniandes.vinilos.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.BuildConfig
import co.edu.uniandes.vinilos.models.DataResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.http.GET
import java.io.File

/** Prueba optativa: verifica URL compilada, permiso de internet, HTTP y Gson dentro de Android. */
@RunWith(AndroidJUnit4::class)
class BackendConnectionTest {
    @Test
    fun backend_returns_albums() =
        runTest {
            val arguments = InstrumentationRegistry.getArguments()
            assumeTrue(
                "Requiere backendSmoke=true y BackVynils encendido",
                arguments.getString("backendSmoke") == "true",
            )
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            // Caché exclusiva de esta prueba; el contenedor tendrá su propia instancia compartida.
            NetworkClient(BuildConfig.BASE_URL, File(context.cacheDir, "backend-smoke-http")).use { client ->
                val api = client.create(ProbeApi::class.java)

                val result = networkRequest { api.albums() }

                assertTrue("GET /albums falló: $result", result is DataResult.Success)
                val albums = (result as DataResult.Success).value
                assertTrue("Se esperaba la semilla de BackVynils", albums.isNotEmpty())
                assertTrue("Faltan id o nombre en /albums", albums.all { it.id != null && !it.name.isNullOrBlank() })
            }
        }

    internal interface ProbeApi {
        @GET("albums")
        suspend fun albums(): List<ProbeAlbumDto>
    }

    internal data class ProbeAlbumDto(
        val id: Int? = null,
        val name: String? = null,
    )
}
