package com.ridesync.app.music

import kotlin.math.abs

/**
 * Client-side music synchronization math.
 *
 * The host is the authoritative clock and periodically publishes:
 *   (track, isPlaying, positionMs, hostTimeMs, syncSeq).
 *
 * A client, knowing its offset to the host clock (via [com.ridesync.app.networking.ClockSync]),
 * computes where playback *should* be right now:
 *
 *   expectedPos = hostPositionAtSample + (hostNow - sampleHostTime)
 *
 * and compares with its actual player position to get the drift. The policy:
 *   - |drift| < soft threshold      -> do nothing (avoid perpetual seeking)
 *   - soft <= |drift| < hard        -> nudge playback speed slightly to
 *                                      converge smoothly over a few seconds
 *   - |drift| >= hard threshold     -> hard seek (a real skip/stall happened)
 *
 * This is deliberately pure so it can be unit-tested against crafted drift
 * scenarios. It emits a [Correction] the player wrapper then applies.
 */
class SyncEngine(
    var softThresholdMs: Long = 120,
    var hardThresholdMs: Long = 300,
) {
    sealed class Correction {
        data object None : Correction()
        data class Speed(val multiplier: Float) : Correction()
        data class Seek(val toPositionMs: Long) : Correction()
    }

    data class HostState(
        val trackId: String?,
        val isPlaying: Boolean,
        val positionMs: Long,
        val hostSampleTimeMs: Long,
        val syncSeq: Long,
    )

    private var latestSeq: Long = -1

    /** The most recent host state we accepted (ignoring stale/out-of-order). */
    var current: HostState? = null
        private set

    /**
     * Ingest a host sync. Returns true if it superseded what we had (newer seq).
     * Out-of-order packets are ignored so late UDP/TCP retransmits don't rewind.
     */
    fun onHostSync(state: HostState): Boolean {
        if (state.syncSeq <= latestSeq) return false
        latestSeq = state.syncSeq
        current = state
        return true
    }

    /**
     * Where playback should be at [hostNowMs] (host clock), for the currently
     * accepted state. Null when paused or no state.
     */
    fun expectedPositionMs(hostNowMs: Long): Long? {
        val state = current ?: return null
        if (!state.isPlaying) return state.positionMs
        val elapsed = hostNowMs - state.hostSampleTimeMs
        return (state.positionMs + elapsed).coerceAtLeast(0)
    }

    /**
     * Compute a correction given the player's actual [actualPositionMs] and the
     * current host clock estimate [hostNowMs].
     */
    fun computeCorrection(actualPositionMs: Long, hostNowMs: Long): Correction {
        val expected = expectedPositionMs(hostNowMs) ?: return Correction.None
        val drift = expected - actualPositionMs // +ve => we are behind, must speed up/seek forward
        val magnitude = abs(drift)
        return when {
            magnitude < softThresholdMs -> Correction.None
            magnitude < hardThresholdMs -> {
                // Nudge speed: behind -> >1.0, ahead -> <1.0. Bounded and gentle.
                val nudge = (drift.toFloat() / CONVERGE_WINDOW_MS).coerceIn(-MAX_SPEED_NUDGE, MAX_SPEED_NUDGE)
                Correction.Speed(1f + nudge)
            }

            else -> Correction.Seek(expected)
        }
    }

    fun reset() {
        latestSeq = -1
        current = null
    }

    companion object {
        /** How aggressively speed correction converges; larger = gentler. */
        const val CONVERGE_WINDOW_MS = 4000f
        const val MAX_SPEED_NUDGE = 0.06f // ±6% — imperceptible for short bursts
    }
}
