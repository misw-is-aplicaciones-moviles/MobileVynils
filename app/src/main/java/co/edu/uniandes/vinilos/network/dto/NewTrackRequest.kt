package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.NewTrack

/** The BackVynils track endpoint accepts exactly these two fields. */
internal data class NewTrackRequest(
    val name: String,
    val duration: String,
)

internal fun NewTrack.toRequest(): NewTrackRequest =
    NewTrackRequest(
        name = name,
        duration = duration,
    )
