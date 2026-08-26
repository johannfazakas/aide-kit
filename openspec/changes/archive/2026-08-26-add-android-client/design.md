# Design — add-android-client

## Context

Changes 1–2 left everything in place: `shared` (contract), `client-core` (API clients + lifecycle-free screen models over an injected `CoroutineScope`), `client` (Compose UI in commonMain, wasmJs target, `App(baseUrl)` entry seam), AGP 9.0.0 pinned-unapplied in the catalog, and Kotlin 2.4.10 whose supported AGP range (8.5.2–9.1.0) was verified in the change-1 spike. Change 2's review recorded the one Android-specific debt: screen models `remember`-ed with a composition scope lose state on activity recreation. The owner's machine has the Android SDK (emulator + adb) at the default location.

## Goals / Non-Goals

**Goals:**

- The same app on Android: both screens, shared models, zero logic duplicated.
- Rotation-proof state: chat session/transcript and task state survive configuration changes.
- Runtime-configurable server address, persisted on device; emulator works out of the box.
- One green light: `./gradlew build` covers the Android target locally and in CI.

**Non-Goals:**

- No TLS, auth, or non-LAN deployment story; cleartext HTTP is accepted deliberately.
- No push/realtime, no offline cache, no Play packaging/signing beyond debug builds.
- No iOS target; no navigation library; no DI framework (unchanged roadmap item).
- No changes to service, web client, or wire contract.

## Decisions

### Android as a target of `client`, not a new module

`client/build.gradle.kts` adds the android target alongside wasmJs, applying `com.android.application` + the pinned AGP — the structure chosen back in the explore session (one UI module, platform targets inside). `androidMain` holds only platform glue: `MainActivity` (`ComponentActivity` + `setContent { App(...) }`), manifest, cleartext allowance. `shared` and `client-core` add the android target through `aidekit.multiplatform-conventions` so all three stay aligned (the conventions plugin gains the target and the androidLibrary configuration; `client` overrides with the application plugin). SDK levels: minSdk 26, compile/target latest stable. *Alternative — separate androidApp module*: rejected in the explore session; revisit only if the platforms diverge structurally.

### Retention: androidx ViewModel holds the client-core models

A small `AppViewModel : androidx.lifecycle.ViewModel` in `androidMain` owns the `HttpClient`, both screen models (constructed with `viewModelScope`), and closes the client in `onCleared()`. `MainActivity` obtains it via `viewModels {}`; `App()` gains an overload/parameters accepting pre-built models so commonMain stays framework-free and the wasm path (composition-scoped, page-lifetime) is untouched. Rotation recreates the activity but reuses the retained ViewModel → transcript, `sessionId`, task state, and in-flight coroutines survive. This is exactly the composition the change-2 review anticipated: androidx provides retention, `client-core` provides logic. *Alternative — androidx lifecycle-viewmodel-compose in commonMain via CMP artifacts*: couples every platform to the lifecycle stack for one platform's need; rejected (consistent with change 2's decision).

### Server address: persisted setting with emulator default

Base URL is read from `SharedPreferences` (default `http://10.0.2.2:7080`, the emulator's host loopback alias) and editable in a lightweight in-app settings affordance (a dialog/screen reachable from the top bar in the Android build — commonMain screens stay unchanged; the affordance lives in androidMain or is passed into `App` as an optional slot). Changing it persists and applies to subsequently created clients (simplest: recreate models via the ViewModel; an app restart note is acceptable fallback if recreation proves fiddly — decide at implementation, record here). *Alternative — build-time constant*: rejected in the explore session (every IP change is a rebuild). *Alternative — DataStore*: heavier dependency for one string; SharedPreferences is enough.

### Cleartext HTTP

The manifest attribute `android:usesCleartextTraffic="true"` grants a blanket cleartext allowance (LAN IPs are arbitrary, so per-domain pins via a network security config file buy nothing here — the attribute is the lighter equivalent mechanism). Documented in README as a deliberate dev-tool posture.

### Install guide: emulator and phone, debug APK over adb

Distribution for the single owner is `adb`-based installation of the debug APK — no signing config, store, or firebase distribution. The guide (README Android section) covers both device paths. **Emulator**: creating an AVD (Android Studio's Device Manager, or `avdmanager`/`sdkmanager` on the CLI), launching it, `./gradlew :client:installDebug` targeting the running emulator — works out of the box since the default URL is the emulator's `10.0.2.2` host alias. **Physical phone**: enabling developer options + USB debugging, `installDebug` with the phone plugged in (or `adb install` of the assembled APK; wireless debugging as the cable-free alternative), then setting the in-app server URL to the laptop's LAN IP with both devices on the same Wi-Fi. The persisted-URL setting is what makes the phone path workable — no rebuild per network. *Alternative — release APK with a local signing key*: adds keystore management for zero benefit at this scale; debug builds are fine for a personal device.

### CI: Android SDK on the runner

The GitHub workflow keeps `./gradlew build` as its single command; a setup step provides the Android SDK (the standard `android-actions/setup-android` or preinstalled SDK on `ubuntu-latest` — verify which suffices during implementation). Accepted cost per the explore decision (everything in CI): slower builds. `local.properties` (machine-local SDK path) joins `.gitignore`.

### Verification

`./gradlew build` green with the android target; existing jvm unit tests unchanged. Live check on the local emulator (SDK present on this machine): both screens against the local service via `10.0.2.2`, a multi-turn tool-calling conversation, rotation mid-conversation preserving session and transcript, and the URL setting switched to the LAN IP and back. Owner assistance may be needed for emulator interaction (AVD creation/UI driving) — flagged, not assumed.

## Toolchain outcome (task 1)

The AGP-9 risk fired exactly as flagged: AGP 9.x refuses `com.android.library`/`com.android.application` alongside the KMP plugin (its replacement is the differently-shaped `com.android.kotlin.multiplatform.library` plus "temporary bypass" flags for apps). Resolved by pinning **AGP 8.13.2** (latest 8.x, inside Kotlin 2.4.10's supported 8.5.2–9.1.0 range, and what CMP templates use); the AGP-9 migration is deferred until CMP documents it. Related settlements: `client` applies `com.android.application` by version-less id (the AGP artifact sits on the conventions-build classpath, so the versioned marker conflicts); the conventions build pins `jvmToolchain(21)` (kotlin-dsl otherwise compiled with the daemon JVM and failed plugin validation); androidx lifecycle pinned **2.10.0** (2.11.0 requires compileSdk 37; we compile against 36); cleartext is granted via the manifest `usesCleartextTraffic` attribute — the lighter equivalent of a network security config file at this scale.

## Risks / Trade-offs

- [AGP 9.0.0 first actually applied here — spike only pinned it] → Change-1 spike verified the compatibility range on paper; task 1 builds a walking skeleton before any feature work, so a version dead-end surfaces immediately with the catalog as the single place to adjust.
- [Compose Multiplatform androidx artifact interplay (compose BOM vs CMP versions)] → CMP's Gradle plugin resolves its own artifact set; add only activity-compose/lifecycle extras, from versions CMP 1.11.1 documents.
- [Cleartext allowance is app-wide] → Acceptable for a personal LAN tool; revisit with any TLS story.
- [Recreating clients on URL change may strand in-flight requests] → Setting changes are rare and user-initiated; models are recreated wholesale (fresh session is acceptable and predictable on an address switch).
- [CI duration grows again] → Accepted (explore decision); Gradle caching bounds the steady state.

## Open Questions

- None blocking. Settled during implementation: URL changes apply **live** — the ViewModel recreates the screen models against the new address immediately (fresh conversation, as designed); GitHub's `ubuntu-latest` image ships a preinstalled Android SDK with accepted licenses, so the workflow keeps `./gradlew build` with no extra setup step (CI on the push is the proof).
