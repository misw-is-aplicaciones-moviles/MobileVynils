package co.edu.uniandes.vinilos.di

import android.content.Context
import androidx.room.Room
import co.edu.uniandes.vinilos.core.DefaultDispatcherProvider
import co.edu.uniandes.vinilos.core.DispatcherProvider
import co.edu.uniandes.vinilos.database.MIGRATION_2_3
import co.edu.uniandes.vinilos.database.MIGRATION_3_4
import co.edu.uniandes.vinilos.database.VinilosDatabase
import co.edu.uniandes.vinilos.database.dao.AlbumDao
import co.edu.uniandes.vinilos.database.dao.CollectorDao
import co.edu.uniandes.vinilos.database.dao.PerformerDao
import co.edu.uniandes.vinilos.database.dao.TrackDao
import co.edu.uniandes.vinilos.network.AlbumServiceAdapterImpl
import co.edu.uniandes.vinilos.network.BackendEndpoint
import co.edu.uniandes.vinilos.network.CollectorServiceAdapterImpl
import co.edu.uniandes.vinilos.network.NetworkClient
import co.edu.uniandes.vinilos.network.PerformerServiceAdapterImpl
import co.edu.uniandes.vinilos.network.PrizeServiceAdapterImpl
import co.edu.uniandes.vinilos.network.VinilosApiService
import co.edu.uniandes.vinilos.repositories.AlbumRepository
import co.edu.uniandes.vinilos.repositories.AlbumRepositoryImpl
import co.edu.uniandes.vinilos.repositories.CollectorRepository
import co.edu.uniandes.vinilos.repositories.CollectorRepositoryImpl
import co.edu.uniandes.vinilos.repositories.PerformerRepository
import co.edu.uniandes.vinilos.repositories.PerformerRepositoryImpl
import co.edu.uniandes.vinilos.repositories.PrizeRepository
import co.edu.uniandes.vinilos.repositories.PrizeRepositoryImpl
import co.edu.uniandes.vinilos.repositories.SessionRepository
import co.edu.uniandes.vinilos.repositories.SessionRepositoryImpl
import okhttp3.OkHttpClient
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executor

/**
 * Crea y guarda las dependencias de la app. Solo VinilosApplication lo instancia.
 * Todo se construye con lazy al primer uso para no frenar el arranque.
 */
class AppContainer(
    context: Context,
    val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
    private val roomExecutor: Executor? = null,
    private val httpCacheEnabled: Boolean = true,
    private val configureHttp: (OkHttpClient.Builder) -> Unit = {},
) : Closeable {
    private val appContext = context.applicationContext

    // Se comparte entre Service Adapters y vive tanto como la aplicación.
    // Su primer acceso se hace fuera del hilo principal, porque prepara la caché HTTP.
    private val networkClientDelegate =
        lazy {
            NetworkClient(
                BackendEndpoint.baseUrl,
                File(appContext.cacheDir, "http"),
                cacheEnabled = httpCacheEnabled,
                configureClient = configureHttp,
            )
        }

    internal val networkClient: NetworkClient by networkClientDelegate

    private val databaseDelegate =
        lazy {
            Room
                .databaseBuilder(appContext, VinilosDatabase::class.java, VinilosDatabase.NAME)
                // Las versiones 3 y 4 solo agregan tablas (artistas y coleccionistas): se migra para conservar lo guardado.
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                // Es una caché del backend: ante un cambio de esquema sin migración se recrea en vez de cerrar la app.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .apply {
                    roomExecutor?.let { executor ->
                        setQueryExecutor(executor)
                        setTransactionExecutor(executor)
                    }
                }.build()
        }

    private val database: VinilosDatabase by databaseDelegate

    override fun close() {
        try {
            if (databaseDelegate.isInitialized()) database.close()
        } finally {
            if (networkClientDelegate.isInitialized()) networkClient.close()
        }
    }

    private val albumDao: AlbumDao by lazy { database.albumDao() }
    internal val trackDao: TrackDao by lazy { database.trackDao() }
    internal val performerDao: PerformerDao by lazy { database.performerDao() }
    internal val collectorDao: CollectorDao by lazy { database.collectorDao() }

    val sessionRepository: SessionRepository by lazy { SessionRepositoryImpl() }

    val prizeRepository: PrizeRepository by lazy {
        PrizeRepositoryImpl(
            PrizeServiceAdapterImpl(
                apiProvider = { networkClient.create(VinilosApiService::class.java) },
                ioDispatcher = dispatchers.io,
                defaultDispatcher = dispatchers.default,
            ),
        )
    }

    // Construirlo no toca networkClient: el adaptador crea la API en su primera llamada.
    val albumRepository: AlbumRepository by lazy {
        AlbumRepositoryImpl(
            albumDao,
            trackDao,
            AlbumServiceAdapterImpl(
                apiProvider = { networkClient.create(VinilosApiService::class.java) },
                ioDispatcher = dispatchers.io,
                defaultDispatcher = dispatchers.default,
            ),
        )
    }

    // Igual que albumRepository: el adaptador crea la API en su primera llamada, fuera del hilo principal.
    val performerRepository: PerformerRepository by lazy {
        PerformerRepositoryImpl(
            performerDao,
            PerformerServiceAdapterImpl(
                apiProvider = { networkClient.create(VinilosApiService::class.java) },
                ioDispatcher = dispatchers.io,
                defaultDispatcher = dispatchers.default,
            ),
        )
    }

    // Igual que albumRepository: el adaptador crea la API en su primera llamada, fuera del hilo principal.
    val collectorRepository: CollectorRepository by lazy {
        CollectorRepositoryImpl(
            collectorDao,
            CollectorServiceAdapterImpl(
                apiProvider = { networkClient.create(VinilosApiService::class.java) },
                ioDispatcher = dispatchers.io,
                defaultDispatcher = dispatchers.default,
            ),
            defaultDispatcher = dispatchers.default,
        )
    }
}
