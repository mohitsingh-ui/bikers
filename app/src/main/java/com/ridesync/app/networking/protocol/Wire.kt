package com.ridesync.app.networking.protocol

import com.ridesync.app.domain.model.Rider
import com.ridesync.app.domain.model.RideInfo
import com.ridesync.app.domain.model.RideSummary
import com.ridesync.app.domain.model.RiderState
import com.ridesync.app.domain.model.TrackInfo
import com.ridesync.app.domain.model.TrackSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * RideSync local wire protocol.
 *
 * Control plane: newline-delimited compact JSON over TCP ([Envelope] per line).
 * Voice/clock plane: small binary datagrams over UDP ([VoicePackets]).
 * Discovery: NSD (mDNS) plus UDP broadcast probes on [Ports.DISCOVERY_UDP].
 *
 * Everything here is pure JVM (no Android imports) so it is unit-testable.
 */
object Wire {

    const val PROTOCOL_VERSION = 1

    /** JSON codec for the control plane. Lenient about unknown fields so old
     *  and new app versions can ride together. */
    val json: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(envelope: Envelope): String = json.encodeToString(Envelope.serializer(), envelope)

    /**
     * Decodes one line into an [Envelope]. Malformed or incompatible input
     * returns null instead of throwing — a bad packet must never crash a ride.
     */
    fun decode(line: String): Envelope? {
        if (line.isBlank() || line.length > MAX_CONTROL_LINE) return null
        return try {
            val env = json.decodeFromString(Envelope.serializer(), line)
            if (env.v > PROTOCOL_VERSION) null
            else if (env.senderId.isBlank() || env.senderId.length > 64) null
            else if (env.rideId.length > 64) null
            else env
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Upper bound for one control line; anything larger is dropped. */
    const val MAX_CONTROL_LINE = 32 * 1024
}

object Ports {
    /** Reliable control messages (TCP, JSON lines). */
    const val CONTROL_TCP = 52780

    /** Voice frames + clock sync (UDP, binary). */
    const val VOICE_UDP = 52781

    /** Broadcast discovery probes/replies (UDP, text+JSON). */
    const val DISCOVERY_UDP = 52782

    /** NSD/mDNS service type. */
    const val NSD_SERVICE_TYPE = "_ridesync._tcp."
}

// ---------------------------------------------------------------------------
// Envelope
// ---------------------------------------------------------------------------

@Serializable
data class Envelope(
    val v: Int = Wire.PROTOCOL_VERSION,
    val senderId: String,
    val rideId: String,
    val seq: Long,
    val sentAt: Long,
    val msg: ControlMessage,
)

// ---------------------------------------------------------------------------
// Control messages
// ---------------------------------------------------------------------------

@Serializable
sealed class ControlMessage

/** Client -> host: request to join a ride. */
@Serializable
@SerialName("JOIN_RIDE")
data class JoinRequest(
    val riderName: String,
    val pin: String,
    val protocol: Int = Wire.PROTOCOL_VERSION,
) : ControlMessage()

/** Host -> client: join granted, here is the world. */
@Serializable
@SerialName("JOIN_ACCEPTED")
data class JoinAccepted(
    val yourKey: Int,
    val ride: RideInfoDto,
    val roster: List<RiderDto>,
    val succession: List<String>,
    val hostTimeMs: Long,
    val rideStarted: Boolean,
) : ControlMessage()

/** Host -> client: join refused. */
@Serializable
@SerialName("JOIN_REJECTED")
data class JoinRejected(val reason: String) : ControlMessage() {
    companion object {
        const val REASON_WRONG_PIN = "WRONG_PIN"
        const val REASON_FULL = "RIDE_FULL"
        const val REASON_VERSION = "INCOMPATIBLE_VERSION"
        const val REASON_UNKNOWN_RIDE = "UNKNOWN_RIDE"
    }
}

/** Host -> all: authoritative roster + ride info. */
@Serializable
@SerialName("RIDER_UPDATE")
data class RosterUpdate(
    val ride: RideInfoDto,
    val roster: List<RiderDto>,
    val succession: List<String>,
) : ControlMessage()

/** Client -> host: alive + device status. */
@Serializable
@SerialName("HEARTBEAT")
data class Heartbeat(
    val batteryPercent: Int? = null,
    val rttMs: Int? = null,
) : ControlMessage()

/** Host -> all: alive + host clock for coarse sync/backup. */
@Serializable
@SerialName("HOST_HEARTBEAT")
data class HostHeartbeat(val hostTimeMs: Long) : ControlMessage()

/** Anyone -> all (relayed by host): rider started transmitting. */
@Serializable
@SerialName("VOICE_START")
data class VoiceStart(val riderKey: Int, val riderName: String) : ControlMessage()

/** Anyone -> all (relayed by host): rider stopped transmitting. */
@Serializable
@SerialName("VOICE_STOP")
data class VoiceStop(val riderKey: Int) : ControlMessage()

/** Client -> host: music transport request (host decides). */
@Serializable
@SerialName("MUSIC_CMD")
data class MusicCommand(
    val action: String,
    val seekToMs: Long? = null,
    val trackId: String? = null,
) : ControlMessage() {
    companion object {
        const val PLAY = "PLAY"
        const val PAUSE = "PAUSE"
        const val NEXT = "NEXT"
        const val PREVIOUS = "PREVIOUS"
        const val SEEK = "SEEK"
        const val RESYNC = "RESYNC"
        const val SELECT = "SELECT"
    }
}

/** Host -> all: authoritative playback state (the sync engine input). */
@Serializable
@SerialName("PLAYBACK_SYNC")
data class PlaybackSync(
    val track: TrackDto? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val hostTimeMs: Long,
    val syncSeq: Long,
) : ControlMessage()

/** Anyone -> all (relayed by host): needs assistance. */
@Serializable
@SerialName("EMERGENCY")
data class Emergency(
    val riderName: String,
    val atMs: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
) : ControlMessage()

/** Anyone -> all (relayed by host): one-tap alert. */
@Serializable
@SerialName("QUICK_ALERT")
data class QuickAlertMsg(
    val kind: String,
    val riderName: String,
) : ControlMessage()

/** New host -> all (after election): reconnect to me. */
@Serializable
@SerialName("HOST_TRANSFER")
data class HostTransfer(
    val newHostId: String,
    val newHostName: String,
    val newHostAddress: String,
    val controlPort: Int,
) : ControlMessage()

/** Host -> all: ride officially begins. */
@Serializable
@SerialName("RIDE_STARTED")
data class RideStarted(val hostTimeMs: Long) : ControlMessage()

/** Host -> all: ride is over; everyone shows the summary. */
@Serializable
@SerialName("RIDE_ENDED")
data class RideEnded(val summary: SummaryDto) : ControlMessage()

/** Either direction: clean goodbye. */
@Serializable
@SerialName("DISCONNECT")
data class Bye(val reason: String? = null) : ControlMessage()

// ---------------------------------------------------------------------------
// DTOs (string-typed enums so future versions stay compatible)
// ---------------------------------------------------------------------------

@Serializable
data class RiderDto(
    val id: String,
    val key: Int,
    val name: String,
    val isHost: Boolean,
    val state: String = "CONNECTED",
    val batteryPercent: Int? = null,
    val talking: Boolean = false,
    val joinedAtMs: Long = 0,
)

@Serializable
data class RideInfoDto(
    val rideId: String,
    val name: String,
    val hostName: String,
    val maxRiders: Int,
    val riderCount: Int,
    val controlPort: Int = Ports.CONTROL_TCP,
    val hostOnlyMusic: Boolean = true,
)

@Serializable
data class TrackDto(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val source: String = "DEMO",
)

@Serializable
data class SummaryDto(
    val rideName: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val riderCount: Int,
    val interruptions: Int,
    val musicSyncedMs: Long,
    val talkSeconds: Long,
)

// ---------------------------------------------------------------------------
// Discovery payloads (UDP broadcast fallback + host-migration claims)
// ---------------------------------------------------------------------------

object DiscoveryWire {
    const val PROBE = "RIDESYNC_DISCOVER_V1"
    const val REPLY_PREFIX = "RIDESYNC_RIDE_V1:"
    const val CLAIM_PREFIX = "RIDESYNC_CLAIM_V1:"

    fun encodeReply(reply: DiscoveryReply): String =
        REPLY_PREFIX + Wire.json.encodeToString(DiscoveryReply.serializer(), reply)

    fun decodeReply(text: String): DiscoveryReply? {
        if (!text.startsWith(REPLY_PREFIX)) return null
        return runCatching {
            Wire.json.decodeFromString(DiscoveryReply.serializer(), text.removePrefix(REPLY_PREFIX))
        }.getOrNull()
    }

    fun encodeClaim(claim: HostClaim): String =
        CLAIM_PREFIX + Wire.json.encodeToString(HostClaim.serializer(), claim)

    fun decodeClaim(text: String): HostClaim? {
        if (!text.startsWith(CLAIM_PREFIX)) return null
        return runCatching {
            Wire.json.decodeFromString(HostClaim.serializer(), text.removePrefix(CLAIM_PREFIX))
        }.getOrNull()
    }
}

@Serializable
data class DiscoveryReply(
    val v: Int = Wire.PROTOCOL_VERSION,
    val rideId: String,
    val name: String,
    val hostName: String,
    val riderCount: Int,
    val maxRiders: Int,
    val controlPort: Int,
    /** Rider id of the current host — used for host-migration tie-breaks. */
    val hostId: String = "",
    /**
     * The ride PIN. Included so a rider who can already SEE the ride on the
     * local network (i.e. is on the host's hotspot — the real security
     * boundary) can join by tapping it. Manual PIN entry remains for riders
     * who can't see it via discovery.
     */
    val pin: String = "",
)

/** Broadcast by a self-promoted host after the previous host vanished. */
@Serializable
data class HostClaim(
    val v: Int = Wire.PROTOCOL_VERSION,
    val rideId: String,
    val hostId: String,
    val hostName: String,
    val controlPort: Int,
    val successionIndex: Int,
)

// ---------------------------------------------------------------------------
// Domain <-> DTO mapping
// ---------------------------------------------------------------------------

fun Rider.toDto(): RiderDto = RiderDto(
    id = id,
    key = key,
    name = name,
    isHost = isHost,
    state = state.name,
    batteryPercent = batteryPercent,
    talking = isTalking,
    joinedAtMs = joinedAtMs,
)

fun RiderDto.toDomain(): Rider = Rider(
    id = id,
    key = key,
    name = name,
    isHost = isHost,
    state = runCatching { RiderState.valueOf(state) }.getOrDefault(RiderState.CONNECTED),
    batteryPercent = batteryPercent,
    isTalking = talking,
    joinedAtMs = joinedAtMs,
)

fun RideInfo.toDto(): RideInfoDto = RideInfoDto(
    rideId = rideId,
    name = name,
    hostName = hostName,
    maxRiders = maxRiders,
    riderCount = riderCount,
    controlPort = controlPort,
    hostOnlyMusic = hostOnlyMusic,
)

fun RideInfoDto.toDomain(hostAddress: String?, pin: String = ""): RideInfo = RideInfo(
    rideId = rideId,
    name = name,
    hostName = hostName,
    pin = pin,
    maxRiders = maxRiders,
    riderCount = riderCount,
    hostAddress = hostAddress,
    controlPort = controlPort,
    hostOnlyMusic = hostOnlyMusic,
)

fun TrackInfo.toDto(): TrackDto = TrackDto(
    id = id,
    title = title,
    artist = artist,
    durationMs = durationMs,
    source = source.name,
)

fun TrackDto.toDomain(uri: String? = null): TrackInfo = TrackInfo(
    id = id,
    title = title,
    artist = artist,
    durationMs = durationMs,
    source = runCatching { TrackSource.valueOf(source) }.getOrDefault(TrackSource.EXTERNAL),
    uri = uri,
)

fun RideSummary.toDto(): SummaryDto = SummaryDto(
    rideName = rideName,
    startedAtMs = startedAtMs,
    durationMs = durationMs,
    riderCount = riderCount,
    interruptions = interruptions,
    musicSyncedMs = musicSyncedMs,
    talkSeconds = talkSeconds,
)

fun SummaryDto.toDomain(): RideSummary = RideSummary(
    rideName = rideName,
    startedAtMs = startedAtMs,
    durationMs = durationMs,
    riderCount = riderCount,
    interruptions = interruptions,
    musicSyncedMs = musicSyncedMs,
    talkSeconds = talkSeconds,
)
