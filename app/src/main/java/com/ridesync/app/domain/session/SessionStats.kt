package com.ridesync.app.domain.session

import com.ridesync.app.domain.model.RideSummary

/**
 * Accumulates the numbers shown on the ride-summary screen. No cloud, no
 * persistence beyond the local recent-rides list.
 */
class SessionStats(private val rideName: String) {
    var startedAtMs: Long = 0L
        private set

    private var interruptions = 0
    private var musicPlayingSinceMs: Long = -1
    private var musicSyncedMs: Long = 0
    private var talkStartedMs: Long = -1
    private var talkMs: Long = 0
    private var peakRiders = 1

    fun onRideStarted(now: Long) {
        startedAtMs = now
    }

    fun onInterruption() {
        interruptions++
    }

    fun onRiderCount(count: Int) {
        if (count > peakRiders) peakRiders = count
    }

    fun onMusicPlaying(playing: Boolean, now: Long) {
        if (playing && musicPlayingSinceMs < 0) {
            musicPlayingSinceMs = now
        } else if (!playing && musicPlayingSinceMs >= 0) {
            musicSyncedMs += now - musicPlayingSinceMs
            musicPlayingSinceMs = -1
        }
    }

    fun onTalking(talking: Boolean, now: Long) {
        if (talking && talkStartedMs < 0) {
            talkStartedMs = now
        } else if (!talking && talkStartedMs >= 0) {
            talkMs += now - talkStartedMs
            talkStartedMs = -1
        }
    }

    fun build(now: Long): RideSummary {
        onMusicPlaying(false, now)
        onTalking(false, now)
        return RideSummary(
            rideName = rideName,
            startedAtMs = if (startedAtMs > 0) startedAtMs else now,
            durationMs = (now - (if (startedAtMs > 0) startedAtMs else now)).coerceAtLeast(0),
            riderCount = peakRiders,
            interruptions = interruptions,
            musicSyncedMs = musicSyncedMs,
            talkSeconds = talkMs / 1000,
        )
    }
}
