package co.edu.uniandes.vinilos.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormErrorFocusTest {
    @Test
    fun `a submit that shows the first errors moves the focus`() {
        assertTrue(errorsJustAppeared(hadErrors = false, hasErrors = true))
    }

    @Test
    fun `a rebuilt view with errors keeps the focus where it is`() {
        assertFalse(errorsJustAppeared(hadErrors = null, hasErrors = true))
    }

    @Test
    fun `fixing one field while others still fail keeps the focus where it is`() {
        assertFalse(errorsJustAppeared(hadErrors = true, hasErrors = true))
    }

    @Test
    fun `a form without errors keeps the focus where it is`() {
        assertFalse(errorsJustAppeared(hadErrors = false, hasErrors = false))
        assertFalse(errorsJustAppeared(hadErrors = true, hasErrors = false))
    }
}
