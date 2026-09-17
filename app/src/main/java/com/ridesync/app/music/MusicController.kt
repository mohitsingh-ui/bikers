package com.ridesync.app.music

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ridesync.app.audio.DuckingController
import com.ridesync.app.core.RLog
import com.ridesync.app.data.preferences.Settings
import com.ridesync.app.domain.model.MusicUiState
import com.ridesync.app.domain.model.SyncStatus
import com.ridesync.app.domain.model.TrackInfo
import com.ridesync.app.domain.model.TrackSource
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Wraps Media3 ExoPlayer for RideSync.
 *
 * On the host it is the source of truth: transport controls here generate the
 * [PlaybackSync] snapshots the network layer publishes.
 *
 * On a client it is a follower: it receives host state and applies drift
 * corrections from [SyncEngine], and the transport buttons are disabled when
 * host-only music is on.
 *
 * It also applies music ducking every tick by multiplying the base music
 * volume with [DuckingController.gainAt].
 *
 * All ExoPlayer calls run on the main thread (Media3 requirement); the ticking
 * loop uses a main-thread Handler.
 */
class MusicController(
    private val context: Context,
    private val ducking: DuckingController,
) {
    private val main = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null

    private val _state = MutableStateFlow(MusicUiState())
    val state: StateFlow<MusicUiState> = _state

    private var playlist: List<TrackInfo> = emptyList()
    private var currentIndex = 0
    private var baseVolume = 0.8f

    @Volatile private var isFollower = false
    @Volatile private var speaking = false

    /** Host publishes snapshots through this; set by the session layer. */
    var onHostSnapshot: ((TrackInfo?, isPlaying: Boolean, positionMs: Long) -> Unit)? = null

    private val sync = SyncEngine()

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            main.postDelayed(this, TICK_MS)
        }
    }

    fun initialize(settings: Settings) {
        if (player != null) return
        baseVolume = settings.musicVolume
        ducking.duckLevel = settings.duckLevel
        ducking.fadeMs = settings.duckFadeMs
        ducking.holdMs = settings.duckHoldMs
        ducking.enabled = settings.duckingEnabled
        sync.softThresholdMs = settings.driftSoftMs.toLong()
        sync.hardThresholdMs = settings.driftHardMs.toLong()

        val exo = ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false,
            )
            repeatMode = Player.REPEAT_MODE_ALL
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    updateState()
                    if (!isFollower) publishSnapshot()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    currentIndex = currentMediaItemIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
                    updateState()
                    if (!isFollower) publishSnapshot()
                }
            })
        }
        player = exo
        loadDemoPlaylist()
        main.post(ticker)
        RLog.i(RLog.Cat.MUSIC, "music controller initialized")
    }

    fun setFollower(follower: Boolean) {
        isFollower = follower
        updateState()
    }

    fun applySettings(settings: Settings) {
        baseVolume = settings.musicVolume
        ducking.duckLevel = settings.duckLevel
        ducking.fadeMs = settings.duckFadeMs
        ducking.holdMs = settings.duckHoldMs
        ducking.enabled = settings.duckingEnabled
        sync.softThresholdMs = settings.driftSoftMs.toLong()
        sync.hardThresholdMs = settings.driftHardMs.toLong()
        updateState()
    }

    fun setSpeaking(isSpeaking: Boolean) {
        speaking = isSpeaking
        ducking.setSpeaking(isSpeaking, System.currentTimeMillis())
    }

    // ------------------------------------------------------------ demo data

    private fun loadDemoPlaylist() {
        val dir = File(context.filesDir, "demo_tracks")
        val tracks = try {
            DemoTrackGenerator.ensureTracks(dir)
        } catch (e: Exception) {
            RLog.w(RLog.Cat.MUSIC, "demo track prep failed", e)
            DemoTrackGenerator.playlistMetadata()
        }
        playlist = tracks
        val exo = player ?: return
        val items = tracks.mapNotNull { t -> t.uri?.let { MediaItem.fromUri(it) } }
        if (items.isNotEmpty()) {
            exo.setMediaItems(items)
            exo.prepare()
            exo.volume = effectiveVolume()
        }
        updateState()
    }

    // ------------------------------------------------------- host transport

    fun hostPlay() = onMain {
        player?.play()
    }

    fun hostPause() = onMain {
        player?.pause()
        publishSnapshot()
    }

    fun hostNext() = onMain {
        player?.seekToNextMediaItem()
        publishSnapshot()
    }

    fun hostPrevious() = onMain {
        player?.seekToPreviousMediaItem()
        publishSnapshot()
    }

    fun hostSelect(trackId: String) = onMain {
        val idx = playlist.indexOfFirst { it.id == trackId }
        if (idx >= 0) {
            player?.seekTo(idx, 0)
            currentIndex = idx
            publishSnapshot()
        }
    }

    fun hostSeek(positionMs: Long) = onMain {
        player?.seekTo(positionMs)
        publishSnapshot()
    }

    fun hostResync() = onMain {
        publishSnapshot()
    }

    fun setBaseVolume(volume: Float) = onMain {
        baseVolume = volume.coerceIn(0f, 1f)
        player?.volume = effectiveVolume()
        updateState()
    }

    // --------------------------------------------------- client follower path

    /**
     * Apply a host snapshot on a follower. [hostSampleTimeMs] is the host clock
     * at which the snapshot's position was true; [hostNowMs] the current host
     * clock estimate (from ClockSync).
     */
    fun onHostState(
        track: TrackInfo?,
        isPlaying: Boolean,
        positionMs: Long,
        hostSampleTimeMs: Long,
        hostNowMs: Long,
        syncSeq: Long,
    ) = onMain {
        val accepted = sync.onHostSync(
            SyncEngine.HostState(track?.id, isPlaying, positionMs, hostSampleTimeMs, syncSeq),
        )
        if (!accepted) return@onMain
        val exo = player ?: return@onMain

        // Switch track if needed.
        if (track != null && track.id != playlist.getOrNull(currentIndex)?.id) {
            val idx = playlist.indexOfFirst { it.id == track.id }
            if (idx >= 0) {
                exo.seekTo(idx, sync.expectedPositionMs(hostNowMs) ?: positionMs)
                currentIndex = idx
            }
        }

        if (isPlaying && !exo.isPlaying) exo.play()
        if (!isPlaying && exo.isPlaying) exo.pause()

        // Immediate coarse align on a fresh snapshot, then let tick() fine-tune.
        val expected = sync.expectedPositionMs(hostNowMs)
        if (expected != null && isPlaying) {
            val drift = kotlin.math.abs(expected - exo.currentPosition)
            if (drift >= sync.hardThresholdMs) {
                exo.seekTo(expected)
                _state.value = _state.value.copy(syncStatus = SyncStatus.RESYNCING)
            }
        }
        updateState()
    }

    /** Follower: current host clock estimate feeder for the tick loop. */
    @Volatile
    var hostClockProvider: (() -> Long)? = null

    private fun tick() {
        val exo = player ?: return
        if (isFollower && sync.current?.isPlaying == true) {
            val hostNow = hostClockProvider?.invoke()
            if (hostNow != null) {
                when (val correction = sync.computeCorrection(exo.currentPosition, hostNow)) {
                    is SyncEngine.Correction.None -> {
                        exo.playbackParameters = PlaybackParameters(1f)
                        if (_state.value.syncStatus == SyncStatus.RESYNCING) {
                            _state.value = _state.value.copy(syncStatus = SyncStatus.SYNCED)
                        }
                    }

                    is SyncEngine.Correction.Speed -> {
                        exo.playbackParameters = PlaybackParameters(correction.multiplier)
                    }

                    is SyncEngine.Correction.Seek -> {
                        exo.seekTo(correction.toPositionMs)
                        exo.playbackParameters = PlaybackParameters(1f)
                        _state.value = _state.value.copy(syncStatus = SyncStatus.RESYNCING)
                    }
                }
            }
        }
        // Ducking gain applied continuously.
        exo.volume = effectiveVolume()
        updateState()
    }

    private fun effectiveVolume(): Float {
        val duckGain = ducking.gainAt(System.currentTimeMillis())
        return (baseVolume * duckGain).coerceIn(0f, 1f)
    }

    private fun publishSnapshot() {
        val exo = player ?: return
        val track = playlist.getOrNull(currentIndex)
        onHostSnapshot?.invoke(track, exo.isPlaying, exo.currentPosition.coerceAtLeast(0))
    }

    fun currentSnapshot(): Triple<TrackInfo?, Boolean, Long> {
        val exo = player
        val track = playlist.getOrNull(currentIndex)
        return Triple(track, exo?.isPlaying ?: false, exo?.currentPosition ?: 0L)
    }

    private fun updateState() {
        val exo = player
        val track = playlist.getOrNull(currentIndex)
        val duration = exo?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: (track?.durationMs ?: 0L)
        val syncStatus = when {
            isFollower && sync.current != null -> _state.value.syncStatus.takeIf {
                it == SyncStatus.RESYNCING
            } ?: SyncStatus.SYNCED
            isFollower -> SyncStatus.IDLE
            track?.source == TrackSource.EXTERNAL -> SyncStatus.LOCAL_ONLY
            else -> SyncStatus.SYNCED
        }
        _state.value = MusicUiState(
            track = track,
            isPlaying = exo?.isPlaying ?: false,
            positionMs = exo?.currentPosition?.coerceAtLeast(0) ?: 0L,
            durationMs = duration,
            syncStatus = syncStatus,
            canControl = !isFollower,
            playlist = playlist,
            volume = baseVolume,
            trackAvailableLocally = track?.uri != null,
        )
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    fun release() {
        main.removeCallbacks(ticker)
        onMain {
            player?.release()
            player = null
        }
        sync.reset()
        RLog.i(RLog.Cat.MUSIC, "music controller released")
    }

    companion object {
        const val TICK_MS = 250L
    }
}
