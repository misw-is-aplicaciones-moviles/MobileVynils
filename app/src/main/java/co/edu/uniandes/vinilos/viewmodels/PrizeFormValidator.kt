package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.NewPrize

/**
 * Rules of the new prize form (HU13): the three fields are required. A field with only spaces is empty,
 * even though the backend would accept it (its Joi schema only rejects the empty string). Pure Kotlin, no Android.
 */
object PrizeFormValidator {
    fun validate(form: PrizeForm): PrizeFormResult {
        val errors =
            PrizeFormErrors(
                name = requiredText(form.name),
                description = requiredText(form.description),
                organization = requiredText(form.organization),
            )
        if (errors.hasErrors) return PrizeFormResult.Invalid(errors)
        return PrizeFormResult.Valid(
            NewPrize(
                name = form.name.trim(),
                description = form.description.trim(),
                organization = form.organization.trim(),
            ),
        )
    }

    private fun requiredText(value: String): FieldError? = if (value.isBlank()) FieldError.REQUIRED else null
}

/** One error, or null, per field of [PrizeForm]. */
data class PrizeFormErrors(
    val name: FieldError? = null,
    val description: FieldError? = null,
    val organization: FieldError? = null,
) {
    val hasErrors: Boolean
        get() = name != null || description != null || organization != null
}

/** Either the prize ready to send or the errors to show. */
sealed interface PrizeFormResult {
    data class Valid(
        val prize: NewPrize,
    ) : PrizeFormResult

    data class Invalid(
        val errors: PrizeFormErrors,
    ) : PrizeFormResult
}
