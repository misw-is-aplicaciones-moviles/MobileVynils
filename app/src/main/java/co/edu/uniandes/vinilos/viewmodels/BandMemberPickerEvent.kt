package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.PerformerSummary

sealed interface BandMemberPickerEvent {
    data class Added(
        val member: PerformerSummary,
    ) : BandMemberPickerEvent

    /** The last POST has an unknown result: the band detail reloads to show what the backend saved. */
    data object ReviewBand : BandMemberPickerEvent

    data object Close : BandMemberPickerEvent

    data object OpenRoleSelector : BandMemberPickerEvent
}
