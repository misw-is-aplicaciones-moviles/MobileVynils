package co.edu.uniandes.vinilos.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("MagicNumber") // Fixture ids.
class SqlVariablesTest {
    @Test
    fun `stale ids are those missing from the kept list`() {
        assertEquals(listOf(listOf(1, 4)), staleIdChunks(current = listOf(1, 2, 3, 4), kept = listOf(2, 3, 5)))
    }

    @Test
    fun `nothing to delete gives no statements`() {
        assertTrue(staleIdChunks(current = listOf(1, 2), kept = listOf(1, 2, 3)).isEmpty())
        assertTrue(staleIdChunks(current = emptyList(), kept = emptyList()).isEmpty())
    }

    @Test
    fun `more stale ids than one statement allows are split without losing any`() {
        val current = (1..2_100).toList()

        val chunks = staleIdChunks(current = current, kept = (1..50).toList())

        assertTrue(chunks.all { it.size <= MAX_IDS_PER_STATEMENT })
        assertEquals((51..2_100).toList(), chunks.flatten())
    }

    @Test
    fun `the chunk size stays below the 999 variables of old SQLite`() {
        assertTrue(MAX_IDS_PER_STATEMENT < 999)
    }
}
