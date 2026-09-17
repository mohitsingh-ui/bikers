package com.ridesync.app.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ridesync.app.domain.model.QuickAlertKind
import com.ridesync.app.domain.model.SessionEvent
import com.ridesync.app.domain.session.RideSession
import kotlinx.coroutines.flow.collectLatest

/**
 * Bridges transient [SessionEvent]s to a Snackbar and to an "emergency banner"
 * callback. Ride-ended events are routed to [onRideEnded] so navigation can
 * show the summary.
 */
@Composable
fun SessionEventEffects(
    session: RideSession?,
    snackbar: SnackbarHostState,
    onRideEnded: (com.ridesync.app.domain.model.RideSummary) -> Unit,
    onEmergency: (SessionEvent.EmergencyReceived) -> Unit,
) {
    LaunchedEffect(session) {
        session ?: return@LaunchedEffect
        session.events.collectLatest { event ->
            when (event) {
                is SessionEvent.RiderJoined -> snackbar.showSnackbar("${event.name} joined the ride")
                is SessionEvent.RiderLeft -> snackbar.showSnackbar("${event.name} left the ride")
                is SessionEvent.RiderReconnecting -> snackbar.showSnackbar("${event.name} lost connection. Reconnecting…")
                is SessionEvent.RiderReconnected -> snackbar.showSnackbar("${event.name} reconnected")
                is SessionEvent.HostChanged -> snackbar.showSnackbar("${event.name} is the new host")
                SessionEvent.HostLost -> snackbar.showSnackbar("Host disconnected. Please reconnect to a new host.", duration = SnackbarDuration.Long)
                is SessionEvent.QuickAlertReceived ->
                    snackbar.showSnackbar("${event.kind.emoji} ${event.riderName}: ${alertText(event.kind)}")
                is SessionEvent.EmergencyReceived -> onEmergency(event)
                SessionEvent.Resyncing -> snackbar.showSnackbar("Resynchronizing music…")
                is SessionEvent.RideEnded -> onRideEnded(event.summary)
                is SessionEvent.Info -> snackbar.showSnackbar(event.message)
            }
        }
    }
}

private fun alertText(kind: QuickAlertKind): String = when (kind) {
    QuickAlertKind.STOPPING -> "Stopping"
    QuickAlertKind.FUEL -> "Fuel stop"
    QuickAlertKind.BREAK -> "Taking a break"
    QuickAlertKind.SLOW_DOWN -> "Slow down"
    QuickAlertKind.TURNING -> "Turning"
    QuickAlertKind.HAZARD -> "Hazard ahead"
}
