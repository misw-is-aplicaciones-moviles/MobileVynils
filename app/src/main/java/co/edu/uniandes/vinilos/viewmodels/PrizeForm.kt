package co.edu.uniandes.vinilos.viewmodels

/** What the collector has typed so far; any field may still be empty. */
data class PrizeForm(
    val name: String = "",
    val description: String = "",
    val organization: String = "",
)
