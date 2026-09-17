# RideSync Architecture

Clean, layered, and modular so networking, audio, UI and music sync evolve
independently.

```
        UI (Jetpack Compose, Material 3)
                 │  observes StateFlow
        ViewModel (RideSyncViewModel)
                 │
        Domain / Session  (RideSession: Host | Client | Simulated)
                 │
   ┌─────────────┼───────────────┬───────────────┐
Repository     Networking       Audio            Music
(DataStore)   (discovery/host/  (mic/voice/      (controller/
              client/protocol)   bluetooth/mixer)  sync engine)
```

The UI only ever talks to a single `RideSession` interface, so the exact same
screens drive hosting, joining, and the on-device simulator — and survive a
host-migration swap underneath.

## Modules (packages under `com.ridesync.app`)

- **`core`** — `RLog` (category-tagged ring-buffer logging; never logs voice),
  `BatteryReader`.
- **`domain.model`** — pure data (`Rider`, `RideInfo`, `TrackInfo`,
  `MusicUiState`, `SessionEvent`, …). No Android deps.
- **`domain.session`** — orchestration:
  - `RideSession` — the interface the UI uses.
  - `HostSession` / `ClientSession` — wire the engines together for each role.
  - `SimulatedSession` — scripted, fully in-memory demo on one device.
  - `SessionManager` — owns the active session and role transitions (create,
    join, simulate, promote-to-host).
  - `SessionEnvironment` — process-wide shared engines and repositories.
  - `SessionStats` — ride-summary accumulation.
- **`data.preferences`** — DataStore repositories: `SettingsRepository`,
  `ProfileRepository` (stable rider UUID), `RecentRidesRepository`.
- **`networking`**
  - `protocol` — `Wire` (JSON control envelopes), `VoicePackets` (binary UDP),
    `RideCodes` (PIN + QR deep link), `Ports`.
  - `transport` — `TcpJsonConnection`, `UdpChannel`.
  - `discovery` — `HostAnnouncer` + `RideScanner` (NSD/mDNS **and** UDP
    broadcast fallback).
  - `host` — `HostServer` (accept loop, roster, voice relay, watchdog),
    `HostElection` (deterministic succession).
  - `client` — `HostConnection` (resilient link, reconnection, clock loop).
  - `ClockSync`, `NetworkMonitor`.
- **`audio`** — `VoiceEngine` (capture→encode→send, receive→decode→mix→play),
  `VoiceCodec` (Opus via Concentus, PCM fallback), `JitterBuffer`,
  `DuckingController`, `VoiceActivityDetector`, `BluetoothAudioManager`,
  `Haptics`, `Tones`.
- **`music`** — `MusicController` (Media3 ExoPlayer wrapper; host-authoritative
  or follower), `SyncEngine` (drift policy), `DemoTrackGenerator` (deterministic
  on-device WAV synth), `MediaIntegration` (pluggable sources + honest fallback).
- **`service`** — `RideSessionService` (foreground service, wake/Wi-Fi locks).
- **`ui`** — `theme`, reusable `components`, and one package per screen
  (`onboarding`, `home`, `createride`, `joinride`, `ride`, `settings`,
  `audiocheck`, `summary`, `devmode`, `scanner`) plus `navigation`.

## Concurrency model

- **Control plane** (TCP JSON) and orchestration run on coroutines
  (`Dispatchers.IO` for blocking socket work), exposing `StateFlow`/`SharedFlow`
  to Compose.
- **Real-time audio** runs on dedicated **max-priority threads** — one for mic
  capture, one for the playback mixer — paced to the 20 ms frame clock with
  `LockSupport.parkNanos`. Coroutine dispatch latency is avoided on the hot path.
- **Voice relay** on the host happens synchronously inside the UDP receive
  callback: one datagram in → N out, keeping added latency in the hundreds of
  microseconds.

## Voice path

```
Mic (AudioRecord, VOICE_COMMUNICATION)
  → platform AEC / NS / AGC
  → gain + [VAD in Open Intercom]
  → Opus encode (20 ms, 16 kHz mono; PCM fallback)
  → UDP to host → relayed to peers
                    → per-sender JitterBuffer (reorder, conceal, bound latency)
                    → decode → mix (soft-clip sum) → AudioTrack
```

Ducking multiplies music gain by `DuckingController.gainAt(now)` every tick;
`anySpeaking` (local PTT/VAD **or** any active remote) drives it.

## Music sync

The host publishes `PLAYBACK_SYNC {track, playing, positionMs, hostTimeMs,
syncSeq}`. Clients know their offset to the host clock (NTP-style, via
`ClockSync`) and compute the expected position; `SyncEngine` returns **None**
(within soft threshold), a bounded **Speed** nudge (mid band), or a **Seek**
(beyond hard threshold). Demo tracks are byte-identical on every device because
`DemoTrackGenerator` renders them deterministically from fixed specs.

## Reconnection & host migration

- `HostConnection` reconnects with capped exponential backoff + jitter; the host
  keeps a rider's slot (matched by stable UUID) so a returning phone resumes.
- The host watchdog moves silent riders CONNECTED → RECONNECTING → DISCONNECTED
  and broadcasts roster changes.
- On permanent host loss with migration enabled, a client runs a **staggered
  election** (`HostElection`): it spends its stagger window **re-discovering** the
  ride; if a lower-ranked peer already became host (same `rideId`, new address) it
  rejoins, otherwise it promotes itself. This avoids split-brain. If the dead host
  was also the hotspot, the network is gone and the app falls back to
  "reconnect to a new host".

## Extensibility

- More riders: `HostServer` keys riders by a compact int and has no hard-coded
  count; `maxRiders` is a setting.
- New music services: implement `MediaIntegration`.
- Future features (GPS group tracking, route, history, iOS, larger groups) fit
  the same layering without touching the audio/voice core.
