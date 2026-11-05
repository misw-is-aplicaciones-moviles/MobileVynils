package co.edu.uniandes.vinilos.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import co.edu.uniandes.vinilos.database.dao.AlbumDao
import co.edu.uniandes.vinilos.database.dao.CollectorDao
import co.edu.uniandes.vinilos.database.dao.PerformerDao
import co.edu.uniandes.vinilos.database.dao.TrackDao
import co.edu.uniandes.vinilos.database.entities.AlbumEntity
import co.edu.uniandes.vinilos.database.entities.CollectorEntity
import co.edu.uniandes.vinilos.database.entities.PerformerEntity
import co.edu.uniandes.vinilos.database.entities.TrackEntity

// Sin singleton propio: la única instancia la crea AppContainer.
@Database(
    entities = [AlbumEntity::class, TrackEntity::class, PerformerEntity::class, CollectorEntity::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
internal abstract class VinilosDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao

    abstract fun trackDao(): TrackDao

    abstract fun performerDao(): PerformerDao

    abstract fun collectorDao(): CollectorDao

    companion object {
        const val NAME = "vinilos.db"
    }
}
