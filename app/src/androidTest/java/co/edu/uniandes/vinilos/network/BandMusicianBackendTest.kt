package co.edu.uniandes.vinilos.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.testing.BackendPerformers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comportamiento real de BackVynils al asociar un músico que ya tiene banda. La app no envía ese caso: esta prueba
 * deja constancia de por qué la regla de una sola banda se aplica en el cliente. Crea y borra sus propios artistas.
 */
@RunWith(AndroidJUnit4::class)
class BandMusicianBackendTest {
    @get:Rule val data = BackendPerformers()

    @Test
    fun adding_a_musician_of_another_band_moves_it_instead_of_rejecting_it() {
        val unique = System.nanoTime()
        val musician = data.createMusician("E2E movido $unique")
        val first = data.createBand("E2E primera $unique", members = listOf(musician))
        val second = data.createBand("E2E segunda $unique")

        data.addMember(second, musician)

        assertEquals(emptySet<Int>(), data.memberIds(first))
        assertEquals(setOf(musician), data.memberIds(second))
    }
}
