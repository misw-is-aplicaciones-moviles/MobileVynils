package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.NewPrize

// Exactly the three fields of the backend schema: BackVynils answers 400 to any extra field, such as id.
internal data class NewPrizeRequest(
    val name: String,
    val description: String,
    val organization: String,
)

internal fun NewPrize.toRequest(): NewPrizeRequest =
    NewPrizeRequest(name = name, description = description, organization = organization)
