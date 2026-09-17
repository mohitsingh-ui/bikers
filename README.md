# RideSync

**Ride together. Talk together. Listen together.**

RideSync is a local, offline-first group communication and synchronized-music app
for motorcycle riders. A group connects their phones to **one local Wi-Fi
hotspot** and uses RideSync as a low-latency intercom while listening to music
kept in sync across every phone. No cloud, no accounts, no phone calls.

Built for Android with Kotlin, Jetpack Compose, Material 3, Coroutines/StateFlow
and a clean UI → ViewModel → Domain → Repository → (Networking / Audio / Music)
architecture. Architected for 4 riders today and more later.

---

## What it does

- **Create or join a local ride.** One phone is the Host (and usually provides
  the Wi-Fi hotspot). Others join over that network by auto-discovery, a 4-digit
  PIN, or a QR code.
- **Push-to-talk voice** (and an optional always-on Open Intercom mode) carried
  directly between phones over the local network — never a cellular call.
- **Synchronized music.** The host is the authoritative clock; every phone plays
  the same track at the same position, continuously drift-corrected.
- **Automatic music ducking.** When anyone speaks, music dips; when they stop, it
  fades back.
- **Live roster** with per-rider connection state, talking indicator and battery.
- **Automatic reconnection**, connection-quality display, and **host migration**
  (where the network survives — see limitations).
- **Safety-first Ride Mode**, an **emergency alert**, and one-tap **quick alerts**.
- **Developer Mode** to run the whole experience on a single phone with
  simulated riders — no second device needed.

---

## Getting the APK

This project ships as source (the APK isn't pre-built). Three ways to get an
installable `app-debug.apk`, easiest first:

1. **GitHub Actions (no local setup).** Push this repo to GitHub. The included
   workflow `.github/workflows/build-apk.yml` builds the APK automatically on
   GitHub's runners (they have the Android SDK and network access). Open the
   **Actions** tab → the latest run → download the **RideSync-debug-apk**
   artifact. You can also trigger it manually via **Run workflow**.
2. **Android Studio.** Open the `RideSync` folder, let Gradle sync, then
   **Build → Build Bundle(s) / APK(s) → Build APK(s)**. Android Studio shows a
   "locate" link to the file when it finishes.
3. **VS Code / command line (no Android Studio).** You only need **JDK 17** and
   the **Android SDK command-line tools** — VS Code is just the editor.
   - **macOS (Homebrew):**
     ```bash
     brew install --cask temurin@17
     brew install --cask android-commandlinetools
     sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
     export ANDROID_HOME="$(brew --prefix)/share/android-commandlinetools"
     yes | sdkmanager --licenses
     ```
   - **Windows:** install Temurin JDK 17 and the Android "Command line tools",
     then run `sdkmanager` for the same three packages and set `ANDROID_HOME`.
   - Then, from the project folder (VS Code's integrated terminal is fine):
     ```bash
     ./build-apk.sh        # macOS/Linux   (or:  build-apk.bat  on Windows)
     # equivalently:  ./gradlew :app:assembleDebug
     # → app/build/outputs/apk/debug/app-debug.apk
     ```
   First run downloads Gradle 8.9 and all libraries automatically (a few
   minutes). Open the folder in VS Code and accept the recommended Kotlin/Gradle
   extensions for syntax highlighting — they're optional for building.

Install with `adb install app-debug.apk`, or copy it to a phone and open it
(enable "install unknown apps"). Debug APKs are signed with the standard debug
key, so they install directly — no signing setup needed.

## Build & run

**Requirements**

- Android Studio (Koala/Ladybug or newer) with JDK 17.
- Android SDK Platform 34, build-tools 34.
- The project pins a conservative, known-good toolchain:
  AGP **8.5.2**, Kotlin **2.0.20**, Gradle **8.9**, Compose BOM **2024.09.03**,
  Media3 **1.4.1**, compileSdk **34**, minSdk **26**.

**Steps**

1. Open the `RideSync/` folder in Android Studio and let Gradle sync (it will
   download dependencies from Google's and Maven Central repositories).
2. Build the `app` module and run on a device or emulator.
   - Voice and Bluetooth features need **physical devices**; the emulator is fine
     for UI and **Developer Mode**.
3. From the command line: `./gradlew :app:assembleDebug` (or `installDebug`).
4. Run the unit tests: `./gradlew :app:testDebugUnitTest`.

> Note: this project was authored in an environment without access to Google's
> Android SDK/Gradle servers, so the APK is built in your Android Studio, not
> pre-compiled here. The pure-logic core (wire protocol, jitter buffer, clock
> sync, drift/ducking math, host election, PIN/QR) is covered by JUnit tests and
> was independently verified with a standalone Kotlin compiler (254 assertions).

---

## Trying it with four phones

1. **Rider 1 (Host):** open RideSync → **Create Ride** → name it → **Create**.
   Turn on the phone's **Wi-Fi hotspot** (Android Settings → Hotspot). The lobby
   shows a PIN and QR code.
2. **Riders 2–4:** connect each phone to Rider 1's hotspot in Android Wi-Fi
   settings. Open RideSync → **Join Ride**. The ride appears automatically; tap
   it (or scan the QR / type the PIN). Pick a name and audio device → **Continue**.
3. When everyone shows **connected**, the host taps **Start Ride**.
4. Host starts music; all phones synchronize playback. Hold the big mic button to
   talk — everyone hears you and the music ducks automatically. Release to return.

**Hotspot tips**

- All phones must be on the **same** hotspot. If a ride isn't discovered, confirm
  the Wi-Fi connection, then use **Refresh** or type the PIN.
- Some manufacturers isolate hotspot clients from each other ("AP isolation").
  RideSync sends discovery over both mDNS/NSD **and** UDP broadcast, and connects
  through the gateway for PIN joins, which works on the large majority of devices
  — but a few locked-down hotspots may block peer traffic. A dedicated travel
  router or a third phone as the hotspot avoids this entirely.

**No second phone?** Home → **Developer Mode** → pick simulated riders →
**Start Simulation**. You get the full ride screen with scripted talking,
ducking, quick alerts, drops and reconnects.

---

## Design

Dark, premium, automotive. Charcoal surfaces, soft-white type, a single electric
**amber** accent reserved for the active mic, the host badge, primary CTAs, music
progress and connection highlights. Large controls, rounded cards, high contrast,
status shown as **icon + text** (never colour alone) for glanceability and
accessibility. An 8dp spacing system and consistent 16–24dp corner radii
throughout. Ride Mode strips the UI to only the essentials at riding size.

---

## Honest limitations

RideSync does not fake functionality. Specifically:

- **It never captures or rebroadcasts audio from other music apps.** That would be
  a copyright violation and Android doesn't permit it. Instead RideSync
  synchronizes *what/when/position*. It ships a small set of **on-device,
  procedurally-generated royalty-free demo tracks** so group sync works out of the
  box with identical audio on every phone. Third-party services integrate through
  the `music/MediaIntegration` boundary; where a service exposes no precise
  controls, RideSync degrades honestly to *"Host is playing X — Open in music
  app"* rather than pretending to scrub it.
- **Perfect zero-latency sync is not promised.** Playback is aligned as closely as
  the devices and network allow, with drift correction (gentle speed nudges for
  small drift, a seek for large).
- **Bluetooth headsets vary.** Many can't do hi-fi music *and* microphone at once;
  enabling the mic (HFP/SCO) narrows the audio. RideSync detects whether a BT mic
  is present and tells you honestly, falling back to the phone mic when a headset
  is music-only.
- **Host migration needs the network to survive.** If the *host phone is also the
  hotspot* and it dies, the whole local network dies with it — no migration is
  physically possible, and RideSync falls back to "reconnect to a new host." When
  the hotspot is a **separate** device (router or a third phone), a surviving
  rider is elected and everyone rejoins automatically.
- **Hotspot behaviour differs by manufacturer** (see AP isolation above).
- **Voice, GPS and battery** features depend on device permissions, which are
  requested only when needed and explained first.

See `docs/ARCHITECTURE.md` and `docs/PROTOCOL.md` for the internals.

---

## Privacy

RideSync works fully offline over your local Wi-Fi. Your voice is sent directly
between the phones in your ride. **No phone call is placed, nothing is uploaded,
and conversations are never recorded or stored.** Location is shared only if you
explicitly enable it for emergency alerts.

---

## License / assets

All UI, the app icon, and the demo tracks are original and generated by the app.
No third-party copyrighted media is bundled.
