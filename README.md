# aide-kit

A personal aid application: a backend where AI agents help manage aspects of daily life, with web and Android clients. The current slice is task management, available through a REST API, a conversational AI assistant, and the client apps; the long-term vision adds more agent capabilities.

## Architecture

```
 browser ──▶ web (nginx, :7081)          Compose Multiplatform wasm app
                │  fetch (CORS)
                ▼
 phone ─────▶ service (Ktor, :7080)      Android app calls the API directly
 REST/chat ─▶    │
                ├─ /api/v1/tasks ──▶ TaskService ──▶ TaskRepository (in-memory | Obsidian vault)
                └─ /api/v1/chat ──▶ Koog agent ── TaskTools ──▶ TaskService
                                        │
                                        ▼
                          OpenCode Zen gateway (glm-5.2)
```

- Gradle modules: `service/` (the Ktor application), `shared/` (the wire contract — transfer models, `kotlinx-datetime` dates as ISO `yyyy-MM-dd`), `client-core/` (client-side logic: per-area API clients and screen models, shared by both clients), and `client/` (Compose Multiplatform UI: web via wasmJs and the Android app from the same screens). The service depends only on `shared` — never on client modules. Shared build config lives in a `gradle/plugins` convention plugin.
- The assistant (package `agent`) is a [Koog](https://github.com/JetBrains/koog) agent wired through the `koog-ktor` plugin, running a custom strategy graph (`agent/AssistantStrategy.kt`) that keeps executing tool calls until a response contains none, so multi-step flows complete reliably. It holds per-session conversation memory (in-memory, bounded — see [Chat](#chat)) and can list tasks and topics, create, update, and complete tasks, but not delete them (the web UI can, via REST). A task's grouping is its `topic` (one of the known topics, or none) and its completion state is `done`; the assistant files tasks only under known topics and clarifies rather than inventing one. A current-date tool lets it resolve relative due dates like "tomorrow" or "next Friday" on its own.
- The task store is chosen by the active startup profile at the composition root (see [Configuration](#configuration)): `local` (default) uses an ephemeral in-memory store, `live` uses a git clone of an Obsidian vault that is the live source of truth. The vault backend supports list, get, and create; update and delete return `501` (deferred). The known-topics list is vault-owned (`organization/Topics.md`) under the `live` profile and a seeded list under `local`.
- Requirements and change history live in `openspec/` ([OpenSpec](https://github.com/Fission-AI/OpenSpec) workflow: specs under `openspec/specs/`, changes under `openspec/changes/`).

## Tech Stack

- Kotlin Multiplatform (JDK 21), Ktor 3, kotlinx.serialization/datetime/coroutines, Gradle with version catalog
- Compose Multiplatform for the clients — wasmJs for the web (nginx serves the bundle) and the Android app from the same screens
- Koog agent framework (`koog-ktor` plugin) with an OpenAI-compatible client against OpenCode Zen
- Koin (`koin-ktor`) for service dependency injection, wired at the composition root

## Configuration

The service starts under one of two profiles, selected by `APP_PROFILE` (default `local`):

- `local` — ephemeral in-memory task storage, lost on restart. No extra configuration.
- `live` — the Obsidian vault backend (see [Task storage](#task-storage)).

Each profile is a HOCON file bundled with the service (`application.conf` base plus `application-local.conf` / `application-live.conf`); the files declare which settings a profile needs and read every value from system environment variables, so secrets stay in the environment and are never committed. Both profiles **require** `OPENCODE_API_KEY` (an [opencode.ai](https://opencode.ai) key) and fail fast at startup without it. Put it in a gitignored `.env` at the repo root — Docker Compose reads it natively:

```shell
APP_PROFILE=local                               # or live; default local
OPENCODE_API_KEY=<your key>
OPENCODE_BASE_URL=https://opencode.ai/zen/go    # Go subscription; omit for pay-per-token /zen
```

Optional: `PORT` (service port, default `7080`; must be an integer — startup fails on garbage) and `CORS_ALLOWED_ORIGINS` (comma-separated, normalized for trailing slashes and case; defaults to loopback origins — localhost, 127.0.0.1, [::1] — on any port; set explicitly for any non-localhost deployment). An unknown `APP_PROFILE` aborts startup, naming the allowed profiles.

### Task storage

The `live` profile stores tasks in a git-managed clone of an Obsidian vault (the live source of truth); `local` keeps them in memory. The `live` profile **requires** the following and fails fast at startup when either is missing:

```shell
APP_PROFILE=live
OBSIDIAN_REPO_URL=https://github.com/<owner>/<vault>.git
OBSIDIAN_REPO_TOKEN=<github token>              # fine-grained PAT, single repo, Contents read/write
OBSIDIAN_REPO_BRANCH=main                        # optional, default main
OBSIDIAN_CLONE_DIR=vault-clone                   # optional, default vault-clone under the working dir
```

The service only ever touches its own clone (cloned on first start, reused after), never a live vault directory. The token is a GitHub fine-grained PAT scoped to the single vault repository with Contents read/write only; rotate it like any deploy secret. Nothing in the build contacts a git remote — JGit behavior is tested against local temporary repositories.

## Run

```shell
./gradlew build         # build + lint + all tests (no LLM calls involved; needs an Android SDK — see Android app)
./gradlew installLocal  # build + both docker images (service via Jib, web via its nginx Dockerfile)
docker compose up       # service on http://localhost:7080, web app on http://localhost:7081
```

Compose forwards `APP_PROFILE` (default `live`) and the `OPENCODE_*` / `OBSIDIAN_*` variables from your `.env` into the service container, so a bare `docker compose up` runs the vault backend; set `APP_PROFILE=local` in `.env` for ephemeral in-memory storage. The `live` clone is kept in a named `vault-clone` volume (mounted at `/data`, the default `OBSIDIAN_CLONE_DIR` in compose) so it survives restarts.

Dev loops without Docker:

```shell
set -a; source .env; set +a; ./gradlew run     # service only (alias of :service:run), on :7080; APP_PROFILE picks local/live
./gradlew :client:wasmJsBrowserDevelopmentRun  # web app with hot reload, against the local service
```

`build` never touches Docker; `installLocal` (or `buildImages` for images only) needs a running Docker daemon.

Code style is enforced by [ktlint](https://pinterest.github.io/ktlint/) (`ktlint_official`, configured in `.editorconfig`); `ktlintCheck` is part of `build`, including the `gradle/plugins` included build. CI (GitHub Actions) runs `./gradlew build` on pushes to `main` and pull requests.

## API

| Method | Path                 | Description                                       |
|--------|----------------------|---------------------------------------------------|
| POST   | `/api/v1/tasks`      | Create a task (`topic`, when set, must be a known topic) |
| GET    | `/api/v1/tasks`      | List tasks (optional `?topic=`)                   |
| GET    | `/api/v1/tasks/{id}` | Get a task                                        |
| PUT    | `/api/v1/tasks/{id}` | Full-replace update (also used to mark done); `501` in Obsidian mode |
| DELETE | `/api/v1/tasks/{id}` | Delete a task; `501` in Obsidian mode             |
| GET    | `/api/v1/topics`     | List the known topics                             |
| POST   | `/api/v1/chat`       | Talk to the assistant: `{"message", "sessionId"?}` → `{"sessionId", "reply"}` |

A task is `{"id", "title", "dueDate"?, "topic"?, "done"}` — `topic` groups it and `done` is its completion state (the former `category`/`completed` names are rejected).

Errors are JSON `{"message": "..."}`: `400` invalid input (including an unknown topic), `404` unknown id, `409` vault conflict, `501` operation unsupported by the storage backend, `502` LLM gateway failure.

[api.http](api.http) exercises every endpoint (JetBrains HTTP client format, runnable from IntelliJ).

## Web client

Open [http://localhost:7081](http://localhost:7081) for the web app: a task screen (list with filter, create/edit forms whose topic is picked from the known topics or left unset, completion toggles, delete with confirmation, refresh) and a chat screen. The task list re-fetches when you switch to it or press refresh — useful after the assistant changed tasks in chat. Unsupported operations (edit/delete against Obsidian storage) surface their error through the screen.

Keyboard shortcuts: **Enter** sends the chat message (**Shift+Enter** inserts a newline) and submits the task forms; **Tab / Shift+Tab** moves between form fields; **Ctrl/Cmd+F** opens an in-app find over the chat transcript (match count, Enter/Shift+Enter cycles, Esc closes) — the app renders to a canvas, so the browser's native find can't see its text; on the task screen it focuses the filter field instead.

## Android app

The same screens ship as an Android app (debug builds installed over adb — no store, no signing setup). State survives rotation, and the server address is a persisted in-app setting ("Server" in the top bar), defaulting to `http://10.0.2.2:7080` — the emulator's alias for the host machine — so the emulator works with zero configuration against a locally running service. Plain HTTP is deliberately allowed (LAN dev tool).

**On an emulator:**

1. Create a virtual device once (Android Studio → Device Manager → Create Device, or `avdmanager create avd`), API 26+.
2. Launch it, then install and run:

```shell
./gradlew :client:installDebug   # installs on the running emulator/connected device
adb shell am start -n ro.jf.ai.assistant/ro.jf.ai.assistant.ui.MainActivity
```

3. With the service running on the host (`./gradlew run` or compose), both screens work immediately via the default address.

**On a phone:**

1. Enable Developer options and USB debugging (Settings → About phone → tap Build number 7×, then Developer options → USB debugging); plug the phone in and accept the debugging prompt (`adb devices` should list it). Wireless debugging (`adb pair`) works cable-free on Android 11+.
2. `./gradlew :client:installDebug` installs the app on the connected device.
3. In the app, open **Server** and set the base URL to your laptop's LAN IP (e.g. `http://192.168.1.20:7080`; find it via `ipconfig getifaddr en0`), with phone and laptop on the same Wi-Fi. The setting persists across restarts.

## Chat

The assistant's conversation has memory, so you can refer back ("mark it done") without repeating task ids. Memory is per session: the first message mints a `sessionId` (returned in the response and reused by the client for follow-ups); omitting it — or reloading the page — starts a fresh conversation; an unknown id (e.g. after a restart) is not adopted — the server mints and returns a fresh one.

**Conversation caveats**: history is in memory only (lost on restart) and bounded to the most recent turns, so a very long conversation forgets its earliest messages.

**Storage caveat**: under the default `local` profile tasks live in memory only — data is lost on restart. Start with `APP_PROFILE=live` (see [Task storage](#task-storage)) to persist them in an Obsidian vault; that backend supports list, get, and create, while update and delete are deferred (they return `501`).
