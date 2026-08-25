# Add Compose web client

## Why

The only UI today is the 95-line static `chat.html` — tasks can't be seen or managed from a browser at all, and the chat page is a stopgap. The restructure (change 1) put the API contract and per-area clients into the shared KMP module precisely so real clients could be built on top; this change builds the first one: a Compose Multiplatform web app, written so its screens and state carry over to the Android client (change 3).

## What Changes

- New `client/` module with a **wasmJs** target (Compose Multiplatform 1.11.1 + compose-compiler, pinned since change 1), Material3, two screens:
  - **Tasks**: list with an in-app filter field, create/edit forms (full-replace PUT), delete with a confirmation dialog — the first UI surface for REST delete, which the agent deliberately lacks. List re-fetches on navigation plus a manual refresh button; no polling or realtime.
  - **Chat**: running transcript, `sessionId` carried across messages in memory (reload starts a fresh conversation), and an "assistant not configured" banner when the service answers 503.
- Client-side logic splits out of `shared/`: a new `client-core/` KMP module receives the API clients (moved from `shared/`) and gains the screen state holders (usable by the Android client later); `shared/` keeps only the wire contract (DTOs), so the service classpath carries no client code. Both modules gain the wasmJs target.
- The web app is served by a **dedicated nginx container**, not by Ktor: the service stops serving static content entirely (`chat.html` and its route deleted) and instead exposes **CORS**, with allowed origins configurable via environment (defaulting to localhost origins for local development and compose).
- **Docker + compose**: both images are built by Gradle (`./gradlew buildImages`) — the service via Jib (JRE base, layered, no Dockerfile), the web via a thin nginx Dockerfile driven by an `Exec` task — and a root `compose.yaml` runs the pair, passing `OPENCODE_API_KEY` through from the root `.env`.
- **BREAKING**: the service now **fails fast at startup when `OPENCODE_API_KEY` is missing** — degraded mode (chat 503 with a working task API) is removed; a misconfigured deployment is loud instead of half-working.
- **Project-specific default ports**, off the crowded 8080: the service listens on **7080** (env-overridable via `PORT`) and the web app is published on **7081**, everywhere — bare Gradle run, compose, docs, `api.http`.
- Dev loop: CMP dev server against the locally-run service, covered by the same CORS defaults.
- `./gradlew build` now also builds the wasm bundle (Docker-free, as ever); `./gradlew installLocal` = build + both images; nothing in the build calls an LLM.

## Capabilities

### New Capabilities

- `web-client`: the browser UI — task management screen (list/filter/create/edit/delete with confirm, refresh semantics) and chat screen (transcript, session continuity, error surfacing), served by a dedicated web container against the CORS-enabled service API.
- `deployment`: container images for the service and the web app, and a root compose file that runs the pair together.

### Modified Capabilities

- `shared-client`: the API clients' home moves from the shared contract module to the client-side `client-core` module; the service depends only on the contract.
- `assistant-chat`: the "Chat web UI" requirement changes from "a minimal static chat page" to the served Compose web app's chat screen (session-per-page-load preserved); the "Degraded mode without LLM configuration" requirement is REMOVED, replaced by fail-fast startup validation of the LLM configuration.

## Impact

- New `client/` module (wasmJs, Compose resources, Material3); `settings.gradle.kts` gains the module.
- New `client-core/` KMP module: API clients + tests move in from `shared/` (`git mv`), state-holder classes + tests added; `shared/` drops its ktor-client dependencies and adds the wasmJs target.
- `service/`: static serving removed (`chat.html`, `web/` resources, and the static route deleted); CORS plugin installed with env-configurable allowed origins; port from `PORT` env (default 7080); startup aborts with a clear error when the API key is absent (existing integration tests switch to a dummy key — nothing in the build calls an LLM); `service/Dockerfile`.
- `README.md` and `CLAUDE.md`: degraded-mode notes replaced by the fail-fast behavior.
- `api.http`: base URL moves to `localhost:7080`.
- Jib config in `service/`, `client/Dockerfile` (nginx + wasm distribution) with a Gradle `Exec` build task, root `buildImages` aggregate, and root `compose.yaml`.
- `gradle/libs.versions.toml`: apply the already-pinned `compose-multiplatform` and `kotlin-compose-compiler` plugins; add CMP library artifacts as needed.
- `README.md`: run story becomes `docker compose up` (plus bare-Gradle dev loops); `.github/workflows/` unchanged commands (build gets slower — wasm compilation).
- Out of scope: Android target/app (change 3 — AGP stays pinned-unapplied), auth, persistence, realtime updates.
