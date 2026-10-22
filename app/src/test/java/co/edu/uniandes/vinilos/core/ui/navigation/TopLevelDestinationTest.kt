package co.edu.uniandes.vinilos.core.ui.navigation

import co.edu.uniandes.vinilos.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun `bottom bar shows albums artists and collectors in prototype order`() {
        val labels = TopLevelDestination.entries.map { it.labelRes }

        assertEquals(listOf(R.string.nav_albums, R.string.nav_artists, R.string.nav_collectors), labels)
    }

    @Test
    fun `each destination points to its own typed route`() {
        assertSame(AlbumList, TopLevelDestination.ALBUMS.route)
        assertSame(ArtistList, TopLevelDestination.ARTISTS.route)
        assertSame(CollectorList, TopLevelDestination.COLLECTORS.route)
        TopLevelDestination.entries.forEach { destination ->
            assertEquals(destination.route::class, destination.routeClass)
        }
    }
}
