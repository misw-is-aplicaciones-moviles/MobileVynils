package co.edu.uniandes.vinilos.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectorInitialsTest {
    @Test
    fun `initials use the first letter of the first and of the last word`() {
        val expected =
            mapOf(
                "Manolo Bellon" to "MB",
                "Ana Lucía Pardo" to "AP",
                "camilo ortega" to "CO",
                "Álvaro Núñez" to "ÁN",
                "Cher" to "C",
                "  Jaime   Monsalve  " to "JM",
                "   " to "",
            )

        expected.forEach { (name, initials) -> assertEquals(name, initials, collectorInitials(name)) }
    }
}
