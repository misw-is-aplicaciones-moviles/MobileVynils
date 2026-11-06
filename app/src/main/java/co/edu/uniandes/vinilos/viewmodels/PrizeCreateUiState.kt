package co.edu.uniandes.vinilos.viewmodels

/** What the notice above the buttons says. Each one maps to a string resource in the Fragment. */
enum class PrizeCreateNotice {
    /** The backend answered 4xx: it did not create the prize. */
    REJECTED,

    /** Timeout, connection lost, 5xx or an unreadable 2xx: the prize may or may not exist. */
    UNCERTAIN,

    /** The catalog could not be read to find out; still uncertain. */
    CHECK_UNAVAILABLE,

    /** The catalog was read and has no such prize: sending again cannot duplicate it. */
    NOT_SAVED,
}

/** Everything the new prize screen draws. [errors] stays empty until the first "Crear premio". */
data class PrizeCreateUiState(
    val form: PrizeForm = PrizeForm(),
    val errors: PrizeFormErrors = PrizeFormErrors(),
    val isSubmitting: Boolean = false,
    val isChecking: Boolean = false,
    val isUncertain: Boolean = false,
    val notice: PrizeCreateNotice? = null,
    val canEdit: Boolean = false,
) {
    /** The fields are locked while a request runs or its result is unknown, so the draft cannot change under it. */
    val isLocked: Boolean get() = isSubmitting || isChecking || isUncertain
    val isBusy: Boolean get() = isSubmitting || isChecking
}
