# Tasks — add-android-client

## 1. Android toolchain activation (walking skeleton)

- [x] 1.1 Apply the pinned AGP: android target in `aidekit.multiplatform-conventions` (androidLibrary config for `shared`/`client-core`), `com.android.application` + android target in `client/` (minSdk 26, latest stable compile/target SDK); catalog gains androidx activity-compose/lifecycle artifacts; `google()` in pluginManagement if needed; `local.properties` gitignored
- [x] 1.2 Walking skeleton: `androidMain` `MainActivity` + manifest rendering `App()` with a hardcoded emulator URL; `./gradlew build` green including `assembleDebug`; record any AGP/version friction in design.md

## 2. Android platform layer

- [x] 2.1 Retained state: `AppViewModel` in `androidMain` owning the `HttpClient` and both screen models on `viewModelScope`, closed in `onCleared()`; `App()` gains a models-injection path so commonMain stays framework-free and wasm is untouched
- [x] 2.2 Server address setting: `SharedPreferences`-persisted base URL (default `http://10.0.2.2:7080`), editable in-app (settings affordance in the Android build), applied by recreating the clients/models; record the live-vs-restart decision in design.md
- [x] 2.3 Cleartext HTTP via network security config; manifest wired (internet permission, launcher activity, app label/icon defaults)

## 3. CI & docs

- [x] 3.1 GitHub workflow: ensure the runner has the Android SDK (verify whether ubuntu-latest's preinstalled SDK suffices; add a setup step if not); `./gradlew build` stays the single command and stays LLM- and Docker-free
- [x] 3.2 `README.md`: Android section (how to run on the emulator, the 10.0.2.2 default, switching to a LAN IP, cleartext caveat); architecture/module notes updated for the android targets
- [x] 3.3 Install guide in the README Android section, covering both paths: emulator (AVD creation via Android Studio Device Manager or avdmanager CLI, launching it, `installDebug`, out-of-the-box 10.0.2.2 default) and physical phone (developer mode + USB debugging, `installDebug` / `adb install` / wireless debugging, in-app URL to the laptop's LAN IP on shared Wi-Fi); emulator path verified in 4.2, phone path on the owner's device if available

## 4. Verification

- [x] 4.1 `./gradlew build` green from clean with the android target; all existing jvm tests unchanged
- [x] 4.2 Live check on the emulator against the local service — DONE, driven end-to-end via adb on the Pixel 3a AVD against the compose-served service: both screens rendered and worked via `10.0.2.2`; chat created a task through a tool call; rotation to landscape preserved transcript and session ("mark it as done" after rotation completed the correct task, API-verified); the Server setting switched to the LAN IP live (list reloaded cross-address) and persisted across force-stop + relaunch. Owner phone install pending whenever convenient (guide in README)
