package co.edu.uniandes.vinilos.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Migraciones 2 → 3 (HU03) y 3 → 4: agregan las tablas de artistas y de coleccionistas sin recrear la base.
 * Usan los esquemas exportados en `app/schemas`, que Gradle agrega como assets de las pruebas instrumentadas.
 */
@RunWith(AndroidJUnit4::class)
class VinilosMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), VinilosDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun deleteDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrating_from_2_to_3_keeps_albums_and_tracks_and_creates_an_empty_performers_table() {
        helper.createDatabase(DATABASE_NAME, 2).apply {
            execSQL(
                "INSERT INTO albums (id, name, cover, releaseDate, description, genre, recordLabel, performerName) " +
                    "VALUES (100, 'Buscando América', 'https://example.com/c.jpg', '1984-08-01', 'D', 'Salsa', " +
                    "'Elektra', 'Rubén Blades')",
            )
            execSQL("INSERT INTO tracks (id, name, duration, albumId) VALUES (1, 'Decisiones', '5:05', 100)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DATABASE_NAME, 3, true, MIGRATION_2_3)

        migrated.query("SELECT name, performerName FROM albums").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("Buscando América", it.getString(0))
            assertEquals("Rubén Blades", it.getString(1))
        }
        migrated.query("SELECT name FROM tracks WHERE albumId = 100").use { assertEquals(1, it.count) }
        migrated.query("SELECT COUNT(*) FROM performers").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun room_opens_the_migrated_file_without_recreating_it_and_the_new_dao_works() =
        runTest {
            helper.createDatabase(DATABASE_NAME, 2).apply {
                execSQL(
                    "INSERT INTO albums (id, name, cover, releaseDate, description, genre, recordLabel, " +
                        "performerName) VALUES (100, 'Buscando América', 'c', '1984-08-01', 'D', 'Salsa', 'E', NULL)",
                )
                close()
            }
            // Sin fallbackToDestructiveMigration: si la migración faltara o fallara, la apertura lanzaría un error.
            val database =
                Room
                    .databaseBuilder(context, VinilosDatabase::class.java, DATABASE_NAME)
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .build()
            try {
                val albums = database.albumDao().getAll()
                assertEquals(listOf("Buscando América"), albums.map { it.name })
                assertEquals(LocalDate.of(1984, 8, 1), albums.single().releaseDate)
                assertEquals(0, database.performerDao().count())
                assertEquals(0, database.collectorDao().count())
            } finally {
                database.close()
            }
        }

    @Test
    fun migrating_from_3_to_4_keeps_albums_tracks_and_performers_and_creates_an_empty_collectors_table() {
        helper.createDatabase(DATABASE_NAME, 3).apply {
            execSQL(
                "INSERT INTO albums (id, name, cover, releaseDate, description, genre, recordLabel, performerName) " +
                    "VALUES (100, 'Buscando América', 'c', '1984-08-01', 'D', 'Salsa', 'Elektra', NULL)",
            )
            execSQL("INSERT INTO tracks (id, name, duration, albumId) VALUES (1, 'Decisiones', '5:05', 100)")
            execSQL(
                "INSERT INTO performers (id, type, name, image, date, albumCount, memberCount, bandId) " +
                    "VALUES (101, 'BAND', 'Queen', 'i', '1970-01-01', 2, 4, NULL)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DATABASE_NAME, 4, true, MIGRATION_3_4)

        listOf("albums", "tracks", "performers").forEach { table ->
            migrated.query("SELECT COUNT(*) FROM $table").use {
                it.moveToFirst()
                assertEquals(table, 1, it.getInt(0))
            }
        }
        migrated.query("SELECT COUNT(*) FROM collectors").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-test"
    }
}
