## Context

`Application.module` (service) is the composition root: it validates env config, installs plugins, constructs `InMemoryTaskRepository` → `TaskService` → `InMemoryConversationStore` inline, and passes them into `installAssistant`, `taskRoutes`, and `chatRoutes` as parameters. Integration tests enter through `module(openCodeApiKey = "test-key", ...)` and rely on its parameter defaults. Constructor injection is already the norm in every class; only the wiring is manual. Koog is installed as a Ktor plugin (`install(Koog)`) inside `installAssistant`.

## Goals / Non-Goals

**Goals:**
- Beans (repository, task service, conversation store) declared once in a Koin module; consumers resolve them instead of receiving them through call chains.
- Tests can substitute beans (e.g., a pre-seeded repository) without new `module()` parameters.
- Startup semantics unchanged: missing `OPENCODE_API_KEY` still fails fast with the same message; all existing integration tests pass unmodified.

**Non-Goals:**
- No DI in client modules; `createScreenModels` stays manual.
- No behavior, API, or deployment changes.
- Environment parsing does not move into Koin — config values are validated exactly where they are today.

## Decisions

- **Koin over manual root / Kodein** (owner decision): standard Ktor integration (`koin-ktor`'s `install(Koin)`), KMP-ready if clients ever need it, minimal DSL.
- **Config-extension pattern**: `KoinConfig.kt` exposes `Application.configureKoin(koinModules)` wrapping `install(Koin)` (SLF4J logger + modules), mirroring the existing `configureStatusPages()` pattern, so `Application.module` stays a flat list of `configureX`/`installX` calls.
- **Bean module**: `serviceModule` in `service/.../config/KoinConfig.kt`:
  `single<TaskRepository> { InMemoryTaskRepository() }`, `single { TaskService(get()) }`, `single<ConversationStore> { InMemoryConversationStore() }` (introducing the interface binding only if a `ConversationStore` abstraction already exists; otherwise bind the concrete class — do not invent new abstractions for DI's sake).
- **Configuration stays parameters**: `Application.module(openCodeApiKey, openCodeBaseUrl, corsAllowedOrigins)` keeps its signature minus the `repository` parameter; env values are not beans. Rationale: they are validated fail-fast scalars, and tests already override them positionally.
- **Test override seam**: `module()` gains an optional `koinModules: List<Module> = listOf(serviceModule)` parameter (or an `extraModules` appended last with `allowOverride`); tests pass a module binding a seeded repository. This replaces the removed `repository` parameter.
  - *Alternative considered*: keeping the `repository: TaskRepository` parameter and binding the instance into Koin — rejected; it preserves the exact pattern DI is meant to replace and mixes the two mechanisms.
- **Resolution style**: resolve once at wiring points (`val service = get<TaskService>()` via `Application.get`/`inject` from `koin-ktor`) and keep passing plain references into `routing { taskRoutes(service) }` route builders. Route functions keep explicit parameters — they stay trivially unit-testable and free of framework types; Koin is used at the composition root only, not sprinkled through layers.
- **Koog stays a Ktor plugin**: `installAssistant(get(), apiKey, baseUrl)` — the assistant install reads its `TaskService` from Koin but Koog's own registration mechanism is untouched.
- **Logging**: install Koin's SLF4J logger so bean issues surface in the service log.

## Risks / Trade-offs

- [Koin resolves at runtime — a missing binding fails at startup request-time, not compile-time] → the bean graph is tiny and `./gradlew build`'s integration tests exercise every binding on app start; a wiring mistake fails CI.
- [Two override mechanisms during transition (params for config, Koin modules for beans)] → documented in CLAUDE.md as the intended split: env config = parameters, beans = Koin.
- [koin-ktor version compatibility with Ktor 3] → use current Koin 4.x line, which targets Ktor 3; verified by the build.

## Migration Plan

Single change: add dependency, introduce `serviceModule`, rewire `Application.module`, adjust tests. No data or deployment migration; rollback = revert the commit.

## Open Questions

None.
