# Restructure for Kotlin Multiplatform

## Why

Two clients are coming — a Compose Multiplatform web app (replacing `chat.html`) and an Android app — and both should reuse the API contract and client plumbing instead of duplicating it. Today the project is a single JVM Gradle module whose DTOs use `java.time.LocalDate`, which does not exist off the JVM. Restructuring first, with server behavior byte-identical, gives the client changes a stable foundation and keeps each step independently verifiable.

## What Changes

- Restructure the single-module build into Gradle modules: `service/` (current application, moved as-is) and `shared/` (new Kotlin Multiplatform module). No client UI yet — that is changes 2 and 3.
- Pin a mutually compatible toolchain set in `gradle/libs.versions.toml` (Kotlin 2.x, Kotlin Multiplatform plugin, Compose Multiplatform plugin, AGP, against Gradle 9.3) via an upfront spike, so later changes don't renegotiate versions.
- Move the `transfer/` DTOs into `shared/` as commonMain code, becoming the single source of truth for the API contract on both sides of the wire.
- Migrate `java.time.LocalDate` → `kotlinx-datetime` `LocalDate` in `Task`, the DTOs, and `TaskTools` date parsing; delete `LocalDateSerializer`. The wire format stays ISO `yyyy-MM-dd` — the REST API and `api.http` behave identically.
- Add multiplatform API clients in `shared/` (Ktor client), split by functional area — `TasksApiClient` for task CRUD and `AssistantApiClient` for chat — ready for the web and Android clients.
- ktlint and the Claude edit hook keep covering all modules; CI's `./gradlew build` builds everything; nothing in the build calls an LLM.

## Capabilities

### New Capabilities

- `shared-client`: the multiplatform shared module — API model wire-format guarantees and per-area API clients for task management and chat.

### Modified Capabilities

_None — REST and chat contracts are unchanged; `code-style` and `ci-pipeline` requirements are already module-layout-agnostic._

## Impact

- `settings.gradle.kts`, root `build.gradle.kts`: multi-module layout; `service/build.gradle.kts`, `shared/build.gradle.kts`; shared build config in a `gradle/plugins` convention plugin.
- `gradle/libs.versions.toml`: toolchain pins from the spike, `kotlinx-datetime`, Ktor client artifacts.
- All of `src/` moves to `service/src/` (`git mv`, history preserved); `transfer/` DTOs move to `shared/`.
- `model/Task.kt`, `transfer/*`, `agent/TaskTools.kt`: `kotlinx-datetime` migration; `transfer/LocalDateSerializer.kt` deleted.
- New `shared/` tests: DTO serialization round-trips, the API clients against Ktor `MockEngine`.
- `.github/workflows/`: unchanged commands, now building both modules; `README.md`: module layout section.
- Changes 2 (`add-compose-web-client`) and 3 (`add-android-client`) build on this and are explicitly out of scope.
