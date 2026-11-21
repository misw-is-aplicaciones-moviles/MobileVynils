package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PerformerAlbumOptionsTest {
    private val free =
        Album(1, "Libre", "", LocalDate.of(1970, 1, 1), "", Genre.ROCK, "", null, emptyList(), emptyList(), emptyList())
    private val own = free.copy(id = 2, name = "Propio")
    private val foreign = free.copy(id = 3, name = "Ajeno", performers = listOf(PerformerSummary(9, "Intérprete")))
    private val state =
        PerformerAlbumPickerUiState(
            albums = listOf(foreign, free, own),
            addedAlbumIds = setOf(2),
            selectedAlbumId = 1,
            phase = AlbumPickerPhase.CONTENT,
            role = UserRole.COLLECTOR,
        )

    @Test
    fun `options preserve input order and only a free selection is enabled`() {
        val rows = buildArtistAlbumOptions(state)
        assertEquals(listOf(3, 1, 2), rows.map { it.album.id })
        assertEquals(
            listOf(AlbumPickerBlock.ASSIGNED_ELSEWHERE, null, AlbumPickerBlock.ALREADY_ADDED),
            rows.map { it.block },
        )
        assertEquals(listOf(false, true, false), rows.map { it.isSelected })
    }

    @Test
    fun `search trims and matches artist or album ignoring case without hiding blocked state`() {
        val artistRows = buildArtistAlbumOptions(state.copy(query = "  INTÉRPRETE  "))
        assertEquals(listOf(3), artistRows.map { it.album.id })
        assertEquals(AlbumPickerBlock.ASSIGNED_ELSEWHERE, artistRows.single().block)
        assertEquals(listOf(1), buildArtistAlbumOptions(state.copy(query = "libRE")).map { it.album.id })
        assertTrue(buildArtistAlbumOptions(state.copy(query = "sin coincidencias")).isEmpty())
        assertTrue(buildArtistAlbumOptions(state.copy(albums = emptyList())).isEmpty())
    }

    @Test
    fun `copy recomputes selection when catalog or selected id changes`() {
        assertEquals(free, state.selectedAlbum)
        assertTrue(state.canConfirm)
        val occupied = state.copy(albums = listOf(free.copy(performers = foreign.performers)))
        assertFalse(occupied.canConfirm)
        assertFalse(buildArtistAlbumOptions(occupied).single().isSelected)
        val otherSelection = state.copy(selectedAlbumId = 2)
        assertEquals(own, otherSelection.selectedAlbum)
        assertFalse(otherSelection.canConfirm)
        assertNull(state.copy(selectedAlbumId = 999).selectedAlbum)
        assertNull(state.copy(albums = emptyList()).selectedAlbum)
    }
}
