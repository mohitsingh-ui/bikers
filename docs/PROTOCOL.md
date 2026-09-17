# RideSync Local Protocol

All traffic is local (no cloud). Three planes:

| Plane | Transport | Port (default) | Payload |
|-------|-----------|----------------|---------|
| Control | TCP, newline-delimited JSON | 52780 | `Envelope` per line |
| Voice + clock | UDP, binary | 52781 | `VoicePackets` datagrams |
| Discovery | UDP broadcast + mDNS/NSD | 52782 | probe/reply text+JSON |

The host runs the TCP server and the UDP voice relay. Clients hold one TCP
connection to the host and send/receive UDP directly to it.

## Control envelope

Every control message is wrapped:

```json
{ "v": 1, "senderId": "<uuid>", "rideId": "<id>", "seq": 1024,
  "sentAt": 1726538492, "msg": { "type": "PLAYBACK_SYNC", ... } }
```

Decoding is defensive: blank/oversized lines, malformed JSON, unknown `type`,
and a `v` newer than supported all return `null` instead of throwing — **a bad
packet never crashes a ride.** `senderId` and `rideId` are length-validated.

### Message types (`msg.type`)

| Type | Dir | Purpose |
|------|-----|---------|
| `JOIN_RIDE` | C→H | request to join (rider name + PIN) |
| `JOIN_ACCEPTED` | H→C | your compact key, ride info, roster, succession, host clock |
| `JOIN_REJECTED` | H→C | `WRONG_PIN` / `RIDE_FULL` / `INCOMPATIBLE_VERSION` / `UNKNOWN_RIDE` |
| `RIDER_UPDATE` | H→all | authoritative roster + ride info + succession |
| `HEARTBEAT` | C→H | alive + battery + measured RTT |
| `HOST_HEARTBEAT` | H→all | alive + host clock (coarse sync fallback) |
| `VOICE_START` / `VOICE_STOP` | any→all | talk indicators (relayed by host) |
| `MUSIC_CMD` | C→H | transport request (host decides; ignored if host-only) |
| `PLAYBACK_SYNC` | H→all | track, playing, positionMs, hostTimeMs, syncSeq |
| `EMERGENCY` | any→all | rider, timestamp, optional lat/lon |
| `QUICK_ALERT` | any→all | preset alert kind |
| `HOST_TRANSFER` | H→all | new host id/name/address/port (migration) |
| `RIDE_STARTED` / `RIDE_ENDED` | H→all | phase changes; ENDED carries the summary |
| `DISCONNECT` | any | clean goodbye |

Every important packet carries type, sender id, ride id, timestamp, and a
sequence number where relevant.

## Voice / clock datagrams (binary, big-endian)

```
[0] magic 0x52 ('R')   [1] version   [2] type
```

- **VOICE** `type=1`: `senderKey:i32, seq:i32, flags:i8, len:i16, payload[len]`.
  `flags`: `START=0x01`, `END=0x02`, `PCM=0x04`. Opus by default (~20 kbps,
  20 ms frames); `PCM` marks fallback frames so peers stay interoperable.
- **CLOCK_PING** `type=2`: `nonce:i64, t0:i64` (client → host).
- **CLOCK_PONG** `type=3`: `nonce:i64, t0:i64, hostTime:i64` (host → client).
- **HELLO** `type=4`: `riderKey:i32` — registers the client's UDP endpoint with
  the host so relay knows where to send (repeated a few times for NAT/AP quirks).

Decoding validates magic, version, length bounds and the declared payload length;
anything malformed returns `null`.

## Clock synchronization

The host is the authoritative clock. Each pong yields one `(offset, rtt)`:

```
rtt    = t2 - t0
offset = hostTime + rtt/2 - t2      // host clock minus local clock
```

`ClockSync` keeps a sliding window and uses the **median offset of the
lowest-RTT half**, rejecting Wi-Fi's asymmetric-delay outliers.
`hostNow() = localNow + offset`.

## Music synchronization

Client expected position for the current `PLAYBACK_SYNC`:

```
expected = positionMs + (hostNow - hostSampleTime)      // while playing
drift    = expected - actualPlayerPosition
```

`SyncEngine` policy: `|drift| < soft` → do nothing; `soft ≤ |drift| < hard` →
bounded playback-speed nudge (±6%) that converges over a few seconds;
`|drift| ≥ hard` → seek. Out-of-order syncs (lower `syncSeq`) are ignored so late
packets never rewind playback.

## Discovery

- **mDNS/NSD**: service type `_ridesync._tcp.`, TXT records `rid, name, hn, hid,
  cnt, max, pin`.
- **UDP broadcast fallback**: clients send `RIDESYNC_DISCOVER_V1`; hosts reply
  `RIDESYNC_RIDE_V1:{json}`. Results from both paths are merged and TTL-expired.

The **PIN** is included in the discovery reply so a rider who can already *see* the
ride (i.e. is on the host's hotspot — the real security boundary) can join by
tapping it. Manual PIN entry remains for riders who can't see it via discovery.

## QR / deep link

`ridesync://join?v=1&rid=…&name=…&hn=…&host=…&port=…&pin=…`, opened by RideSync's
in-app scanner or any generic QR app. Parsing is hostile-input safe (no crashes,
validates IPv4/PIN/port).

## Versioning

`PROTOCOL_VERSION = 1`. Unknown JSON fields are ignored (`ignoreUnknownKeys`), and
peers advertising a newer major version are refused with `INCOMPATIBLE_VERSION`,
so old and new builds interoperate where safe and fail clearly where not.
