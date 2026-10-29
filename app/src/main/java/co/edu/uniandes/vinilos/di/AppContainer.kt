package co.edu.uniandes.vinilos.di

import android.content.Context
import androidx.room.Room
import co.edu.uniandes.vinilos.BuildConfig
import co.edu.uniandes.vinilos.database.VinilosDatabase
import co.edu.uniandes.vinilos.database.dao.AlbumDao
import co.edu.uniandes.vinilos.database.dao.TrackDao
import co.edu.uniandes.vinilos.network.AlbumServiceAdapterImpl
import co.edu.uniandes.vinilos.network.NetworkClient
import co.edu.uniandes.vinilos.network.VinilosApiService
import co.edu.uniandes.vinilos.repositories.AlbumRepository
import co.edu.uniandes.vinilos.repositories.AlbumRepositoryImpl
import co.edu.uniandes.vinilos.repositories.SessionRepository
import co.edu.uniandes.vinilos.repositories.SessionRepositoryImpl
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Crea y guarda las dependencias de la app. Solo VinilosApplication lo instancia.
 * Todo se construye con lazy al primer uso para no frenar el arranque.
 */
class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    // Se comparte entre Service Adapters y vive tanto como la aplicación.
    // Su primer acceso se hace fuera del hilo principal, porque prepara la caché HTTP.
    internal val networkClient: NetworkClient by lazy {
        NetworkClient(BuildConfig.BASE_URL, File(appContext.cacheDir, "http"))
    }

    private val database: VinilosDatabase by lazy {
        Room
            .databaseBuilder(appContext, VinilosDatabase::class.java, VinilosDatabase.NAME)
            // Es una caché del backend: ante un cambio de esquema sin migración se recrea en vez de cerrar la app.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    private val albumDao: AlbumDao by lazy { database.albumDao() }
    internal val trackDao: TrackDao by lazy { database.trackDao() }

    val sessionRepository: SessionRepository by lazy { SessionRepositoryImpl() }

    // Construirlo no toca networkClient: el adaptador crea la API en su primera llamada.
    val albumRepository: AlbumRepository by lazy {
        AlbumRepositoryImpl(
            albumDao,
            AlbumServiceAdapterImpl(
                apiProvider = { networkClient.create(VinilosApiService::class.java) },
                ioDispatcher = Dispatchers.IO,
                defaultDispatcher = Dispatchers.Default,
            ),
        )
    }
}
