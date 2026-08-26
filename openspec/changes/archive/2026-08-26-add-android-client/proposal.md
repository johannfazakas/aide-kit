# Add Android client

## Why

The Android app is the third and final change of the client roadmap, and the payoff of the previous two: the API contract (`shared`), the clients and screen models (`client-core`), and the Compose screens (`client` commonMain) were all built to be reused by a mobile client. This change adds the thin Android layer on top — the same app, on a phone.

## What Changes

- The `client/` module gains an **android application target** (`com.android.application`; AGP was pinned in the catalog since change 1 and lands here at **8.13.2** — the 9.x line hard-blocks the KMP plugin, see design's toolchain outcome): `androidMain` with a `MainActivity` rendering the existing `App()` composable; `shared` and `client-core` gain android targets as needed.
- **Retained state across configuration changes**: the plain `client-core` screen models are held by an androidx `ViewModel` in `androidMain`, so rotation does not reset the chat `sessionId`, transcript, or in-flight requests — resolving the lifetime note recorded in change 2's review. `client-core` itself stays lifecycle-free.
- **In-app server URL setting**: a small settings surface with a persisted, device-local base URL (default `http://10.0.2.2:7080` — the emulator's host alias), so switching to a LAN IP needs no rebuild. This is the Android analogue of the web client's page-host-derived base URL.
- **Cleartext HTTP allowed** explicitly (network security config) — the service is a plain-HTTP LAN/dev tool; no TLS story yet.
- **CI builds the app**: `./gradlew build` now also assembles the Android target, so the GitHub runner needs the Android SDK; the build stays LLM-free and Docker-free.
- **Install guide**: a short, copy-paste-able guide for running the app on both an emulator (AVD creation, launch, `installDebug`, works out of the box via `10.0.2.2`) and a physical phone (developer mode, USB or wireless adb, pointing the in-app server URL at the laptop's LAN IP).
- No service changes: the app calls the REST/chat API directly; CORS is a browser mechanism and does not apply to native clients.

## Capabilities

### New Capabilities

- `android-client`: the Android app — same task and chat screens on shared state, retained across configuration changes, with a persisted server-address setting.

### Modified Capabilities

- `shared-client`: the contract and client-core modules additionally compile for the android target (requirement wording gains the target; behavior unchanged).

## Impact

- `client/build.gradle.kts`: android target + `com.android.application` + compose for android; `androidMain` sources (MainActivity, ViewModel holder, settings persistence, manifest, network security config).
- `shared/build.gradle.kts`, `client-core/build.gradle.kts` (via the multiplatform conventions plugin): android target.
- `gradle/libs.versions.toml`: apply the pinned AGP; androidx activity-compose/lifecycle artifacts; `settings.gradle.kts` pluginManagement `google()` if missing.
- `.github/workflows/`: Android SDK setup step; `README.md`: Android section (run, emulator default URL, LAN switch) and the emulator/phone install guide; `local.properties` gitignored.
- Out of scope: TLS/auth, push/realtime, Play distribution, iOS.
