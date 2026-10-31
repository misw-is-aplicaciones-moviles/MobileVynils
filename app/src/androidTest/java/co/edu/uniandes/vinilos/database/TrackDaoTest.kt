package co.edu.uniandes.vinilos.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

// Nombres en snake_case: los nombres con espacios no son válidos en DEX antes de API 30.
@RunWith(AndroidJUnit4::class)
class TrackDaoTest {
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
    fun get_by_album_returns_only_tracks_of_that_album() =
        runTest {
            albumDao.upsertAll(listOf(album, otherAlbum))
            trackDao.upsertAll(listOf(firstTrack, secondTrack, otherAlbumTrack))

            val tracks = trackDao.getByAlbum(album.id)

            assertEquals(listOf(firstTrack, secondTrack), tracks)
        }

    @Test
    fun deleting_the_album_deletes_its_tracks() =
        runTest {
            albumDao.upsertAll(listOf(album, otherAlbum))
            trackDao.upsertAll(listOf(firstTrack, otherAlbumTrack))

            albumDao.replaceAll(listOf(otherAlbum))

            assertTrue(trackDao.getByAlbum(album.id).isEmpty())
        }

    @Test
    fun upserting_the_album_keeps_its_tracks() =
        runTest {
            albumDao.upsert(album)
            trackDao.upsert(firstTrack)

            albumDao.upsert(album.copy(description = "Descripción actualizada"))

            assertEquals(listOf(firstTrack), trackDao.getByAlbum(album.id))
        }

    @Test(expected = SQLiteConstraintException::class)
    fun inserting_a_track_of_a_missing_album_fails() =
        runTest {
            trackDao.upsert(firstTrack)
        }

    private companion object {
        val album =
            AlbumEntity(
                id = 100,
                name = "Buscando América",
                cover = "https://example.com/buscando.jpg",
                releaseDate = LocalDate.of(1984, 8, 1),
                description = "Primer álbum de Rubén Blades con Seis del Solar.",
                genre = "Salsa",
                recordLabel = "Elektra",
                performerName = null,
            )
        val otherAlbum = album.copy(id = 101, name = "Poeta del pueblo")
        val firstTrack = TrackEntity(id = 1, name = "Decisiones", duration = "5:05", albumId = 100)
        val secondTrack = TrackEntity(id = 2, name = "GDBD", duration = "4:20", albumId = 100)
        val otherAlbumTrack = TrackEntity(id = 3, name = "Te están buscando", duration = "4:36", albumId = 101)
    }
}
