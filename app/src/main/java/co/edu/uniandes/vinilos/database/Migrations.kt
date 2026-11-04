package co.edu.uniandes.vinilos.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Versión 3: HU03 agrega la tabla de artistas. No toca `albums` ni `tracks`, así que el catálogo guardado
 * sobrevive a la actualización en lugar de recrearse con la migración destructiva.
 */
internal val MIGRATION_2_3: Migration =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `performers` (`id` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, `image` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                    "`albumCount` INTEGER NOT NULL, `memberCount` INTEGER NOT NULL, `bandId` INTEGER, " +
                    "PRIMARY KEY(`id`))",
            )
        }
    }
