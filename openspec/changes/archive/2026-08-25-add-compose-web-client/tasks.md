# Tasks — add-compose-web-client

## 1. Toolchain activation & module split

- [x] 1.1 Verify Ktor 3.2.2 client artifacts resolve for wasmJs (bump the single `ktor` ref within this change if not); add the wasmJs target to `shared/`; `./gradlew build` stays green
- [x] 1.2 Create `client-core/` KMP module (jvm + wasmJs, `aidekit.common-conventions`): `git mv` the `client` package (both API clients, `ApiClientSupport`, tests) from `shared/`; `client-core` depends on `shared` via `api(...)`; `shared/` keeps DTOs only and drops its ktor-client dependencies; service unchanged (depends on `shared` only); build green
- [x] 1.3 Create `client/` module: KMP + compose-multiplatform + compose-compiler plugins (already pinned), `wasmJs { browser() }` with `binaries.executable()`, Material3 artifacts, dependency on `client-core/`; hello-world screen compiles via `wasmJsBrowserDistribution`

## 2. Presentation state (client-core)

- [x] 2.1 Add `TasksScreenModel` to `client-core/` commonMain (`presentation` package): task list, filter text, create/edit/delete actions incl. pending-delete confirmation state, loading/error state, refresh; plain class over `TasksApiClient` + injected `CoroutineScope`, `StateFlow` out; add `kotlinx-coroutines-core` dependency
- [x] 2.2 Add `ChatScreenModel`: transcript, in-memory `sessionId` carry, sending state, error message state (covers unreachable service); over `AssistantApiClient`
- [x] 2.3 Unit-test both in commonTest (given-when-then, `MockEngine` + `kotlinx-coroutines-test`): load/filter/create/complete/delete flows, session continuity, error transitions

## 3. Web UI

- [x] 3.1 Build the Tasks screen: list with completion toggles, filter field, create/edit forms (full-replace PUT semantics), delete confirmation dialog, refresh button; re-fetch on navigation to the screen
- [x] 3.2 Build the Chat screen: transcript display, input + send, error display
- [x] 3.3 App shell: Material3 theme, two-destination `NavigationBar`, base-URL resolution in one function (default `http://localhost:7080`)

## 4. Split serving, CORS & containers

- [x] 4.1 Strip static serving from the service: delete `chat.html`, the `web/` resources, and the `staticResources` route; existing integration tests stay green
- [x] 4.1b Make the service port configurable: `PORT` env var, default **7080**; update `api.http` and any docs referencing 8080
- [x] 4.1c Fail-fast startup: abort with a clear error naming `OPENCODE_API_KEY` when absent; replace the 503-degraded integration tests with a startup-failure test and switch remaining tests to a dummy key (build stays LLM-free); update README and CLAUDE.md degraded-mode notes
- [x] 4.2 Install CORS on the service: allowed origins from `CORS_ALLOWED_ORIGINS` (comma-separated, `Application.module` parameter), defaulting to localhost origins on any port when unset; integration-test allowed-origin headers, foreign-origin refusal, and the localhost default
- [x] 4.3 Gradle-driven image builds: service via Jib (`jibDockerBuild`, temurin-21-jre base, host-arch platform), web via `client/Dockerfile` (`nginx:alpine`) built by an `Exec` task depending on the wasm distribution; root `buildImages` aggregate — REVISED on review from plain Dockerfiles; Jib-for-web attempted and rejected (needs a java shim module)
- [x] 4.4 Add root `compose.yaml` referencing the Gradle-built images: `service` on 7080 with `OPENCODE_API_KEY`/`OPENCODE_BASE_URL` passed from the root `.env`, `web` on 7081 with `depends_on`; `./gradlew buildImages && docker compose up` brings both up

## 5. Verification & docs

- [x] 5.1 `./gradlew build` green from clean: service + shared + client-core (jvm and wasmJs) + client bundle, all tests, no LLM calls; CI workflow needs no command changes
- [x] 5.2 Live check through compose (needs `OPENCODE_API_KEY` in `.env`): `docker compose up`, open the web app on :7081, exercise the Tasks screen (create, filter, complete, delete with confirm, refresh) and the Chat screen cross-origin (multi-turn conversation with a tool call — create then "mark it as done" — then refresh Tasks and see it completed); verify reload starts a fresh session; restart the stack without the key and verify the service container exits with a clear error — DONE via compose: web serves the bundle on :7081, CORS granted to :7081 and refused to foreign origins, multi-turn tool-calling chat completed a task cross-origin, REST delete worked, keyless container exited naming OPENCODE_API_KEY. Browser click-through of the canvas UI performed by the owner (both screens working through the compose stack)
- [x] 5.3 Update `README.md`: run story (`./gradlew build` + `docker compose up`, plus bare-Gradle dev loops with the CMP dev server), module layout (`shared` contract / `client-core` / `client`), CORS env var and its localhost default, architecture diagram, remove `chat.html` references
