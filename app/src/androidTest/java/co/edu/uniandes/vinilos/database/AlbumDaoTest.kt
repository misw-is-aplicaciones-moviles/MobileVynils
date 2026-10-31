package co.edu.uniandes.vinilos.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.database.dao.AlbumDao
import co.edu.uniandes.vinilos.database.dao.TrackDao
import co.edu.uniandes.vinilos.database.entities.AlbumEntity
import co.edu.uniandes.vinilos.database.entities.TrackEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

// Nombres en snake_case: los nombres con espacios no son válidos en DEX antes de API 30.
@RunWith(AndroidJUnit4::class)
class AlbumDaoTest {
    private lateinit var database: VinilosDatabase
    private lateinit var albumDao: AlbumDao
    private lateinit var trackDao: TrackDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VinilosDatabase::class.java).build()
        albumDao = database.albumDao()
        trackDao = database.trackDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun get_all_returns_albums_sorted_by_name() =
        runTest {
            albumDao.upsertAll(listOf(poeta, buscando))

            val albums = albumDao.getAll()

            assertEquals(listOf(buscando, poeta), albums)
        }

    @Test
    fun release_date_is_stored_and_read_back_unchanged() =
        runTest {
            albumDao.upsert(buscando)

            val album = albumDao.getAll().single()

            assertEquals(LocalDate.of(1984, 8, 1), album.releaseDate)
        }

    @Test
    fun replace_all_removes_albums_missing_from_the_new_list() =
        runTest {
            albumDao.upsertAll(listOf(buscando, poeta))

            albumDao.replaceAll(listOf(poeta))

            assertEquals(listOf(poeta), albumDao.getAll())
        }

    @Test
    fun replace_all_keeps_tracks_of_albums_that_remain() =
        runTest {
            albumDao.upsert(buscando)
            trackDao.upsert(decisiones)

            albumDao.replaceAll(listOf(buscando.copy(name = "Buscando América (remasterizado)")))

            assertEquals(listOf(decisiones), trackDao.getByAlbum(buscando.id))
        }

    @Test
    fun get_with_tracks_returns_album_and_its_tracks() =
        runTest {
            albumDao.upsertAll(listOf(buscando, poeta))
            trackDao.upsertAll(listOf(decisiones, otraCancion))

            val result = albumDao.getWithTracks(buscando.id)

            assertEquals(buscando, result?.album)
            assertEquals(listOf(decisiones), result?.tracks)
        }

    @Test
    fun get_with_tracks_returns_null_when_album_does_not_exist() =
        runTest {
            val result = albumDao.getWithTracks(id = 999)

            assertNull(result)
        }

    @Test
    fun performer_name_is_stored_and_read_back() =
        runTest {
            albumDao.upsert(buscando)

            assertEquals("Rubén Blades", albumDao.getAll().single().performerName)
        }

    @Test
    fun null_performer_name_is_stored_and_read_back() =
        runTest {
            albumDao.upsert(buscando.copy(performerName = null))

            assertNull(albumDao.getAll().single().performerName)
        }

    @Test
    fun get_all_folds_only_ascii_case_so_accented_names_sort_last() =
        runTest {
            albumDao.upsertAll(listOf(buscando.copy(id = 1, name = "Ágape"), buscando.copy(id = 2, name = "Zeta")))

            assertEquals(listOf("Zeta", "Ágape"), albumDao.getAll().map { it.name })
        }

    private companion object {
        val buscando =
            AlbumEntity(
                id = 100,
                name = "Buscando América",
                cover = "https://example.com/buscando.jpg",
                releaseDate = LocalDate.of(1984, 8, 1),
                description = "Primer álbum de Rubén Blades con Seis del Solar.",
                genre = "Salsa",
                recordLabel = "Elektra",
                performerName = "Rubén Blades",
            )
        val poeta =
            AlbumEntity(
                id = 101,
                name = "Poeta del pueblo",
                cover = "https://example.com/poeta.jpg",
                releaseDate = LocalDate.of(1984, 8, 1),
                description = "Recopilación de Rubén Blades.",
                genre = "Salsa",
                recordLabel = "Elektra",
                performerName = "Rubén Blades",
            )
        val decisiones = TrackEntity(id = 1, name = "Decisiones", duration = "5:05", albumId = 100)
        val otraCancion = TrackEntity(id = 2, name = "Te están buscando", duration = "4:36", albumId = 101)
    }
}
