package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.Track

sealed interface TrackCreateEvent {
    data class Created(
        val track: Track,
    ) : TrackCreateEvent
}
