# Design — add-compose-web-client

## Context

Change 1 left the project multiplatform-ready: `service/` (Ktor + Koog), `shared/` (KMP, jvm-only target, DTOs + `TasksApiClient`/`AssistantApiClient`), conventions in `gradle/plugins`, and the toolchain pinned (Kotlin 2.4.10, CMP 1.11.1, compose-compiler; Gradle 9.3.0). The only UI is static `chat.html` served at `/`. Decisions inherited from the explore session: Compose Multiplatform everywhere (option A, accepting canvas-rendering trade-offs like no browser Ctrl+F — mitigated by an in-app task filter), web replaces `chat.html`, maximal sharing, refresh-on-view + button, in-memory session parity, everything built in CI. Revised on review: serving is split (the service no longer hosts the web app — a dedicated web container does, with permanent CORS on the API) and both deployables ship as Docker images run together by a root compose file.

## Goals / Non-Goals

**Goals:**

- A browser UI for both existing capabilities: task CRUD (including the agent-less delete) and assistant chat.
- Client-side logic (API clients, presentation state) in `client-core/` so Android (change 3) reuses it; `client/` stays a thin UI layer; the service's classpath stays free of client code.
- Split serving: a web container for the bundle, the service as a pure API with CORS; one `docker compose up` runs the pair.
- CI keeps one green light: `./gradlew build` builds service, shared, and the wasm bundle, still LLM-free.

**Non-Goals:**

- No Android target or app (change 3); AGP stays pinned-unapplied.
- No auth, persistence, realtime/push updates, or streaming chat.
- No navigation library, no DI framework yet, no UI test automation this slice.
- No offline support — the app is a live client of the service.
- No image publishing/registry, and no image builds in CI — `build` stays Docker-free; images are an explicit local step.
- No TLS/reverse-proxy layer — plain localhost HTTP between browser and both containers.

## Decisions

### client module: wasmJs-only, Material3, no navigation library

`client/` applies `kotlin-multiplatform`, `compose-multiplatform`, and `kotlin-compose-compiler`; single `wasmJs { browser() }` target with `binaries.executable()`. UI is Material3 with a two-destination `NavigationBar` (Tasks, Chat) switched by plain state — a navigation library is unwarranted for two screens. Screen switching to Tasks triggers a refresh (the agreed freshness model) alongside an explicit refresh action. *Alternative — add the android target now dormant*: rejected in change 1 for the same reason it stays out here; targets arrive with the change that ships them.

### Module architecture: contract in shared, client logic in client-core

Reviewing the original single-`shared` plan surfaced a boundary problem: the service depends on `shared` for DTOs, so anything else placed there (API clients today, screen models tomorrow) lands on the backend classpath with only package discipline keeping it out of backend code. Split by consumer instead:

```
shared (contract: DTOs)  ◀── service
        ▲
        └── client-core (API clients + screen models)  ◀── client (Compose UI)
```

`client-core/` is a new KMP module (jvm + wasmJs targets via a new `aidekit.multiplatform-conventions` precompiled plugin shared with `shared/`): the `client` package (both API clients, `ApiClientSupport`, their tests) moves there from `shared/` (`git mv`), and the screen models are born there. It depends on `shared` via `api(...)` so UI modules see the DTOs transitively, and exposes `ktor-client-core` via `api(...)` since `HttpClient` appears in the clients' public constructors; a single `apiHttpClient()` factory owns content negotiation so consumers hold exactly one configured client. `shared/` keeps only the transfer models and sheds its ktor-client dependencies — the compiler now enforces that the service can't reach client-side classes. *Alternative — keep one shared module with package discipline*: no enforcement, dishonest dependency graph, ktor-client on the service classpath for nothing; rejected on review. *Alternative — screen models in `client/` commonMain without a new module*: satisfies the service-boundary concern but leaves the API clients misplaced and ties the models to the Compose module; rejected.

### Screen state holders live in client-core commonMain

Two plain classes in `client-core/` (new `presentation` package): `TasksScreenModel` (task list + filter text + form/delete state, calls `TasksApiClient`) and `ChatScreenModel` (transcript, in-memory `sessionId`, calls `AssistantApiClient`). They expose `StateFlow` state + suspend/launching actions on an injected `CoroutineScope`, with **no androidx/lifecycle dependency** — that keeps `shared/` platform-clean and lets change 3 wrap them in whatever lifecycle Android wants. Errors map to a user-visible error message state (an unreachable service surfaces there too). Unit-tested in commonTest with `MockEngine`-backed clients and `kotlinx-coroutines-test`. `client-core/` carries an explicit `kotlinx-coroutines-core` dependency; both `shared/` and `client-core/` add the wasmJs target. *Alternative — androidx ViewModel via CMP's lifecycle artifacts*: couples shared to a lifecycle stack Android hasn't justified yet; plain holders are trivially wrappable later.

### Serving: dedicated web container, service becomes a pure API

The wasm distribution is served by an **nginx** container (`client/Dockerfile`: `nginx:alpine` + the `wasmJsBrowserDistribution` output — nginx chosen as the boring default; swapping to Caddy would be a two-line change). The service **stops serving static content**: `chat.html`, the `web/` resources, and the `staticResources` route are deleted, decoupling UI delivery from the backend process (a web redeploy no longer restarts the service and wipes in-memory state). Trade-off accepted: two origins, so CORS becomes a permanent part of the API. *Alternative — embedded serving (original design)*: rejected on review in favor of the split. *Alternative — reverse proxy fronting both (same-origin, no CORS)*: a third container and a hop that localhost use doesn't need; the compose file can grow one later without touching the app.

### CORS: first-class, env-configured

The service always installs Ktor's CORS plugin. Allowed origins come from a `CORS_ALLOWED_ORIGINS` env var (comma-separated, passed as an `Application.module` parameter for testability); when unset it defaults to **localhost origins on any port**, which covers the compose pair (web on `:7081`), the CMP dev server, and local tooling without configuration. Non-localhost deployments must set the variable explicitly. This one mechanism also serves the dev loop — the earlier dev-only `DEV_CORS` flag is subsumed and dropped. Integration tests assert both behaviors (allowed origin echoed, foreign origin refused).

### Fail-fast startup without LLM configuration

Revised on review: instead of the historical degraded mode (start anyway, chat answers 503), the service now **refuses to start when `OPENCODE_API_KEY` is absent**, aborting with a clear message naming the variable. Rationale: with a containerized deployment there is no operator staring at a half-working app — a service that is up-but-crippled hides misconfiguration, while a failed container is immediately visible in `docker compose ps`/logs. Integration tests pass a dummy key (the agent only ever runs on a chat request, and no test completes one — the build stays LLM-free); tests asserting the old 503 behavior are replaced by a startup-failure test. The web client consequently drops its dedicated degraded-banner state: the service is either fully up or not there, and the generic error state covers an unreachable API.

### Ports: project-specific defaults

8080 is the most-squatted dev port around; the stack moves to its own pair: the service listens on **7080** by default (read from a `PORT` env var / `module` parameter, so containers and tests can override) and the web container publishes on **7081**. The same defaults hold for bare `./gradlew run`, compose, `api.http`, and docs, so there is exactly one answer to "which port?" per service.

### Docker images: Gradle-driven, hybrid Jib + Dockerfile

Image building is explicit and Gradle-driven: a root `buildImages` aggregate builds both with correct task dependencies (eliminating any stale-outputs coupling). Image names are single-sourced in `gradle.properties` (`serviceImageName`/`webImageName`; `compose.yaml` uses matching env-overridable defaults), image tasks `mustRunAfter` their project's `check` so `installLocal` cannot tag images from failing code, the web image task declares inputs/outputs (`--iidfile`) so unchanged builds skip Docker, and the Jib platform is overridable via `-PdockerArchitecture` (defaulting from the build JVM's arch). Also, and `installLocal` = full `build` + `buildImages` for the one-command local story. `build` itself deliberately never requires Docker — CI and IDE syncs stay daemon-free (settled after briefly wiring images into `build` and reverting on review). The mechanisms differ by what fits: the **service** uses **Jib** (`jibDockerBuild`: `eclipse-temurin:21-jre` base, dependency/classes layering, no Dockerfile, platform matched to the host architecture). The **web** image stays a thin `client/Dockerfile` (`nginx:alpine` + the wasm distribution) built by a Gradle `Exec` task depending on `wasmJsBrowserDistribution`. *Alternative — Jib for the web image too*: attempted and reverted; Jib requires a java `main` source set, forcing an empty-java shim subproject just to layer static files onto nginx — the plain Dockerfile is the honest tool there. *Alternative — multi-stage docker builds*: slow, duplicate Gradle caches; rejected. Root `compose.yaml` references the built images (`aide-kit-service`, `aide-kit-web`): `service` (7080, env `OPENCODE_API_KEY` passed through from the root `.env`, which compose reads natively; startup refused when absent) and `web` (7081→80, `depends_on` service). 

### Base URL resolution

With split origins there is no same-origin case: the web app targets `http://localhost:7080` by default, kept in one small function (the seam where a runtime config lookup or change 3's Android in-app setting slots in later).

## Risks / Trade-offs

- [Ktor 3.2.2 client artifacts might lack wasmJs variants] → Check first in implementation; if missing, bump the single `ktor` version ref within this change (server + client move together; tests pin behavior).
- [CMP-for-web rough edges (text input, selection, a11y) — chat is text-heavy] → Accepted for a personal tool per the explore decision; the shared-module split keeps a DOM-based web UI as a swap-out escape hatch.
- [Wasm compilation slows every `./gradlew build`] → Accepted (CI-scope decision); Gradle caching limits the cost on unchanged UI.
- [Docker images built from stale Gradle outputs] → `buildImages` owns the full chain via task dependencies (Jib builds from live outputs; the web Exec task depends on the distribution); the live check runs through compose itself.
- [Default-open localhost CORS surprises a future real deployment] → Localhost-only by default, never wildcard; non-localhost requires explicit `CORS_ALLOWED_ORIGINS`, and the risk is called out in README.
- [State holders' scope/threading misuse on wasm] → Holders take an injected scope; wasm is single-threaded so races are minimal; commonTest covers state transitions.

## Open Questions

- None blocking. Exact CMP library artifact set (foundation/material3/resources) is settled during implementation from what the two screens actually need.
