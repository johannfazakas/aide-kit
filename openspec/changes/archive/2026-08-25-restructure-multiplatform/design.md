# Design — restructure-multiplatform

## Context

Single-module JVM Gradle project: root `build.gradle.kts` applies `kotlin-jvm`, `kotlin-serialization`, `ktlint`, and `application`; sources under `src/main/kotlin/ro/jf/ai/assistant/`. DTOs (`transfer/`) already use kotlinx.serialization but carry `java.time.LocalDate` through a custom `LocalDateSerializer` — JVM-only. Koog and Ktor server are JVM concerns; the API contract and a future client are not. Two client changes (Compose web via wasmJs, Android) are queued behind this one and were deliberately excluded from its scope. Decisions inherited from the explore session: option A (Compose Multiplatform everywhere), maximal sharing in `shared/`, everything built in CI.

## Goals / Non-Goals

**Goals:**

- Multi-module layout (`service/`, `shared/`) with server behavior byte-identical — same endpoints, same wire format, same tests passing.
- `shared/` commonMain owns the DTOs and per-area API clients usable from JVM, wasmJs, and Android targets later.
- One pinned, mutually compatible toolchain set in the version catalog, spiked before dependent work.
- ktlint + hook + CI coverage extends over all modules; the build still never calls an LLM.

**Non-Goals:**

- No UI, no composeApp module, no wasm or Android targets activated (changes 2 and 3).
- No CORS flag, no server-address configuration, no auth (belong to the client changes).
- No DI framework (roadmap item; introduced only when composeApp wiring demands it).
- No behavior or API changes of any kind.

## Decisions

### Module layout: service + shared now, composeApp later

`settings.gradle.kts` includes `service` and `shared`. Shared build configuration (ktlint, group/version) lives in a `gradle/plugins` included build as a precompiled convention plugin (`aidekit.common-conventions`), applied explicitly by the root project and each module — no `allprojects`/`subprojects` cross-project configuration, keeping the build configuration-cache-friendly as modules are added. `application`/Koog/Ktor-server config moves into `service/build.gradle.kts`. `shared/build.gradle.kts` applies `kotlin("multiplatform")` with `jvm()` as its only active target for now — wasmJs and android targets are added by the changes that need them, so this change never blocks on wasm/Android toolchain quirks beyond version pinning. *Alternative — activate all targets now*: front-loads risk into the restructure and makes byte-identical verification noisier; rejected.

### Toolchain spike first, pinned in the catalog

Task zero resolves the compatibility matrix (Kotlin 2.x / KMP plugin / Compose Multiplatform plugin / AGP against Gradle 9.3) and records the chosen versions in `gradle/libs.versions.toml` — including plugins not yet applied (CMP, AGP), so changes 2 and 3 inherit decisions instead of renegotiating them. If Gradle 9.3 proves incompatible with the required AGP, prefer adjusting the Gradle wrapper version within the spike and record why.

### DTOs move to shared commonMain; kotlinx-datetime replaces java.time

`transfer/` moves to `shared/` (same package, `git mv`), with `LocalDate` swapped to `kotlinx.datetime.LocalDate`, whose built-in serializer emits the same ISO `yyyy-MM-dd` string — `LocalDateSerializer` is deleted, `@Serializable` annotations stay, the wire format is provably unchanged (round-trip tests assert the exact JSON). `model/Task.kt` (server-internal) also migrates so the service layer speaks one date type; `TaskTools` date parsing switches from `LocalDate.parse` + `DateTimeParseException` to the kotlinx-datetime equivalent (`IllegalArgumentException` on bad input — the guarded error path already catches it, keeping tool error behavior identical). *Alternative — mapping layer between shared DTOs and JVM-internal java.time*: permanent conversion noise to preserve a type the codebase doesn't need; rejected.

### API clients in shared, engine-agnostic, split by functional area

`shared/` gains one client per functional area — `TasksApiClient` (task CRUD including delete — REST has it even though the agent doesn't) and `AssistantApiClient` (`chat(sessionId?, message)`) — each wrapping a caller-supplied `HttpClient` (engine injected per platform later; `MockEngine` in tests) and sharing internal error/negotiation helpers (`ApiException`). Specific clients keep future functionalities from accreting onto one class. They use the shared DTOs and the same content negotiation the server speaks. The server does not consume these clients; integration tests keep Ktor's test client. *Alternative — defer the clients to change 2*: it's the part of `shared/` most worth testing early and has no UI dependency; including it here keeps change 2 purely UI.

### Verification: byte-identical behavior

Server integration tests move untouched and must pass unchanged. Round-trip tests pin the exact date JSON (`"2026-08-24"`). A manual `api.http` pass against the running server closes the loop. No LLM in the build, as ever.

## Risks / Trade-offs

- [Version matrix dead-ends (KMP/CMP/AGP vs Gradle 9.3)] → Spike is task zero with an explicit deliverable (pinned catalog + note in design.md); nothing else starts until it lands.
- [kotlinx-datetime parse semantics differ subtly from java.time (e.g. leniency, error types)] → Round-trip and TaskTools unit tests cover the exact formats; the tool error contract is asserted by existing tests.
- [Moved sources break git history readability] → `git mv` throughout, preserving history.
- [Root-vs-module Gradle config drift (ktlint applied unevenly)] → shared config centralized in the `aidekit.common-conventions` convention plugin (`gradle/plugins/`), applied per module; the lint-clean requirement in `code-style` already demands repo-wide compliance. The included build lints itself too (ktlint applied in its own build, generated kotlin-dsl sources excluded) and is wired into the root `check` via `gradle.includedBuild("plugins").task(":check")` — fault-injection verified a violation in the convention script fails `./gradlew build`.
- [Hook script assumptions about paths] → The hook lints the edited file path directly and is layout-agnostic; verify once during the restructure.

## Spike outcome (task 1.1)

Pinned set (verified 2026-08-24 against the official KMP compatibility guide and CMP release data):

- Kotlin **2.4.10** — supports Gradle 7.6.3–9.5.0, so the existing **Gradle 9.3.0 wrapper stays**; empirically green with Ktor 3.2.2, Koog 1.1.1-beta, and ktlint-gradle 14.2.0 (`./gradlew build` passed on the bumped catalog before any restructuring).
- Compose Multiplatform **1.11.1** (latest stable; tracks latest Kotlin, min 2.1.0) + Kotlin compose-compiler plugin (versioned with Kotlin) — pinned, applied first in change 2.
- AGP **9.0.0** — inside Kotlin 2.4.x's supported 8.5.2–9.1.0 range; pinned, applied and fully validated in change 3 (needs the Android SDK).
- kotlinx-datetime **0.8.0** (plain variant, not the `-0.6.x-compat` one).
- Ktor stays **3.2.2** for server and client artifacts (one version ref; client bumps, if needed, belong to the client changes).

## Open Questions

- None blocking.
