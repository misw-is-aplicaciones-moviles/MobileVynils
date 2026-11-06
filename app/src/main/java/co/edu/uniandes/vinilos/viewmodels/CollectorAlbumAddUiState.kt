package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.AlbumStatus
import co.edu.uniandes.vinilos.models.DataError

data class CollectorAlbumAddUiState(
    val albumName: String = "",
    val artistName: String = "",
    val priceDigits: Int = 0,
    val status: AlbumStatus? = null,
    val isSubmitting: Boolean = false,
    val submitError: DataError? = null,
    val validationAttempted: Boolean = false,
) {
    val priceText: String get() = "$$priceDigits"
    val isPriceInvalid: Boolean get() = validationAttempted && priceDigits <= 0
    val isStatusMissing: Boolean get() = validationAttempted && status == null
    val canSubmit: Boolean get() = priceDigits > 0 && status != null && !isSubmitting
}
