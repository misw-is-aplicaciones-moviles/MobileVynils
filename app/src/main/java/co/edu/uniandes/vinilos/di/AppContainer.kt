package co.edu.uniandes.vinilos.di

import android.content.Context
import androidx.room.Room
import co.edu.uniandes.vinilos.database.VinilosDatabase
import co.edu.uniandes.vinilos.database.dao.AlbumDao
import co.edu.uniandes.vinilos.database.dao.TrackDao

/**
 * Crea y guarda las dependencias de la app. Solo VinilosApplication lo instancia.
 * Todo se construye con lazy al primer uso para no frenar el arranque.
 */
class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val database: VinilosDatabase by lazy {
        Room
            .databaseBuilder(appContext, VinilosDatabase::class.java, VinilosDatabase.NAME)
            // Es una caché del backend: ante un cambio de esquema sin migración se recrea en vez de cerrar la app.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    // Pasan a private cuando existan los repositorios (TR-02, HU01): el contenedor solo expondrá repositorios.
    internal val albumDao: AlbumDao by lazy { database.albumDao() }
    internal val trackDao: TrackDao by lazy { database.trackDao() }
}
