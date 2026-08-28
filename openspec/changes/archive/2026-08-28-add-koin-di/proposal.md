## Why

All service wiring is hand-built in `Application.module`: the repository, task service, and conversation store are instantiated inline and threaded through as parameters. This works at the current size but concentrates construction knowledge in one function and makes every new dependency a signature change; the roadmap explicitly calls for dependency injection. Introducing a DI container now, while the graph is small, keeps the pattern cheap to adopt.

## What Changes

- Add Koin (`koin-ktor`) to the service and declare its beans — task repository, task service, conversation store — in a Koin module installed by the Ktor application.
- Routes and the assistant installation resolve their dependencies from Koin instead of receiving them as function parameters.
- Environment-derived configuration (API key, base URL, CORS origins, port) stays as explicit `Application.module` parameters — it is configuration, not beans, and the fail-fast startup behavior must not change.
- Integration tests keep working through the same `module(...)` entry point; bean-level substitution (e.g., a seeded repository) becomes possible through Koin module overrides.
- Scope: service only. Client modules (`client-core`, `client`) keep their small manual wiring (`createScreenModels`).

## Capabilities

### New Capabilities

- `service-architecture`: internal structure requirements for the service — layered wiring via dependency injection (Koin), a single composition root, and configuration kept separate from the bean graph.

### Modified Capabilities

None — no externally observable behavior changes; existing capability specs (task-management, assistant-chat, deployment) are untouched.

## Impact

- `gradle/libs.versions.toml` — Koin version and `koin-ktor` (+ SLF4J logger) library entries; `service/build.gradle.kts` dependency.
- `service/src/main/kotlin/ro/jf/ai/assistant/Application.kt` — installs Koin with the service module; inline construction removed.
- New DI module declaration under `service/src/main/kotlin/ro/jf/ai/assistant/config/` (alongside the existing config code).
- `routes/`, `agent/Assistant.kt` — signatures move from passed-in dependencies to Koin resolution where it simplifies wiring.
- Service tests: existing integration tests unchanged in behavior; add coverage proving bean override works.
