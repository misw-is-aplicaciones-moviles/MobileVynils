package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.NewPrize
import org.junit.Assert.assertEquals
import org.junit.Test

class PrizeFormValidatorTest {
    private val validForm =
        PrizeForm(
            name = "Voz del Caribe",
            description = "Reconoce a intérpretes que llevaron la música del Caribe a nuevas audiencias.",
            organization = "Asociación de Coleccionistas de Vinilo",
        )

    @Test
    fun `valid form returns the prize with surrounding spaces removed`() {
        val form =
            PrizeForm(
                name = "  Voz del Caribe ",
                description = "\n Reconoce a intérpretes.\t",
                organization = " Asociación de Coleccionistas de Vinilo  ",
            )

        val result = PrizeFormValidator.validate(form)

        val expected =
            NewPrize(
                name = "Voz del Caribe",
                description = "Reconoce a intérpretes.",
                organization = "Asociación de Coleccionistas de Vinilo",
            )
        assertEquals(PrizeFormResult.Valid(expected), result)
    }

    @Test
    fun `spaces and line breaks inside a field are kept`() {
        val form = validForm.copy(description = "Primera línea.\n\nSegunda   línea.")

        val result = PrizeFormValidator.validate(form) as PrizeFormResult.Valid

        assertEquals("Primera línea.\n\nSegunda   línea.", result.prize.description)
    }

    @Test
    fun `each invalid partition flags only its own field`() {
        val required = FieldError.REQUIRED
        val partitions =
            listOf(
                Partition("empty name", validForm.copy(name = ""), PrizeFormErrors(name = required)),
                Partition("blank name", validForm.copy(name = "   "), PrizeFormErrors(name = required)),
                Partition("tab name", validForm.copy(name = " \t"), PrizeFormErrors(name = required)),
                Partition(
                    "empty description",
                    validForm.copy(description = ""),
                    PrizeFormErrors(description = required),
                ),
                Partition(
                    "line breaks description",
                    validForm.copy(description = "\n \n"),
                    PrizeFormErrors(description = required),
                ),
                Partition(
                    "empty organization",
                    validForm.copy(organization = ""),
                    PrizeFormErrors(organization = required),
                ),
                Partition(
                    "no-break space organization",
                    validForm.copy(organization = "   "),
                    PrizeFormErrors(organization = required),
                ),
            )

        partitions.forEach { partition ->
            val result = PrizeFormValidator.validate(partition.form)

            assertEquals(partition.label, PrizeFormResult.Invalid(partition.errors), result)
        }
    }

    @Test
    fun `an empty form flags the three fields`() {
        val required = FieldError.REQUIRED

        val result = PrizeFormValidator.validate(PrizeForm())

        assertEquals(PrizeFormResult.Invalid(PrizeFormErrors(required, required, required)), result)
    }

    @Test
    fun `a field with only spaces is empty even though the backend would accept it`() {
        val form = PrizeForm(name = "   ", description = "   ", organization = "   ")

        val result = PrizeFormValidator.validate(form) as PrizeFormResult.Invalid

        assertEquals(true, result.errors.hasErrors)
        assertEquals(FieldError.REQUIRED, result.errors.name)
        assertEquals(FieldError.REQUIRED, result.errors.description)
        assertEquals(FieldError.REQUIRED, result.errors.organization)
    }

    private data class Partition(
        val label: String,
        val form: PrizeForm,
        val errors: PrizeFormErrors,
    )
}
