# aide-kit

A personal aid application: a backend where AI agents help manage aspects of daily life. The long-term vision includes a mobile client and more agent capabilities; the current slice is task management, available both as a REST API and through a conversational AI assistant.

## Architecture

```
                 ┌──────────────────── Ktor server ────────────────────┐
                 │                                                     │
 REST client ───▶│  /api/v1/tasks ────────────▶ TaskService ──▶ TaskRepository
                 │                                   ▲            (in-memory)
 chat client ───▶│  /api/v1/chat ──▶ Koog agent ── TaskTools           │
                 │                       │                             │
                 └───────────────────────│─────────────────────────────┘
                                         ▼
                          OpenCode Zen gateway (glm-5.2)
```

- `routes` → `service` → `repository` layering; the agent's tools reuse the same `TaskService` as the REST API.
- The assistant (package `agent`) is a [Koog](https://github.com/JetBrains/koog) agent wired through the `koog-ktor` plugin. It is stateless — every chat request is a fresh conversation — and it can list, create, update, and complete tasks, but not delete them.
- Requirements and change history live in `openspec/` ([OpenSpec](https://github.com/Fission-AI/OpenSpec) workflow: specs under `openspec/specs/`, changes under `openspec/changes/`).

## Tech Stack

- Kotlin (JDK 21), Ktor 3 (Netty, kotlinx.serialization), Gradle with version catalog
- Koog agent framework (`koog-ktor` plugin) with an OpenAI-compatible client against OpenCode Zen

## Run

```shell
./gradlew build         # build + lint + all tests (no LLM calls involved)
./gradlew run           # start server on http://localhost:8080
./gradlew ktlintCheck   # lint only
./gradlew ktlintFormat  # auto-fix lint violations
```

Code style is enforced by [ktlint](https://pinterest.github.io/ktlint/) (`ktlint_official`, configured in `.editorconfig`); `ktlintCheck` is part of `build`. CI (GitHub Actions) runs `./gradlew build` on pushes to `main` and pull requests.

Assistant configuration (optional — without it the task API works and chat returns 503):

```shell
export OPENCODE_API_KEY=<your key>                     # opencode.ai key
export OPENCODE_BASE_URL=https://opencode.ai/zen/go    # Go subscription; omit for pay-per-token /zen
```

## API

| Method | Path                 | Description                                       |
|--------|----------------------|---------------------------------------------------|
| POST   | `/api/v1/tasks`      | Create a task                                     |
| GET    | `/api/v1/tasks`      | List tasks (optional `?category=`)                |
| GET    | `/api/v1/tasks/{id}` | Get a task                                        |
| PUT    | `/api/v1/tasks/{id}` | Full-replace update (also used to mark completed) |
| DELETE | `/api/v1/tasks/{id}` | Delete a task                                     |
| POST   | `/api/v1/chat`       | Talk to the assistant: `{"message", "sessionId"?}` → `{"sessionId", "reply"}` |

Errors are JSON `{"message": "..."}`: `400` invalid input, `404` unknown id, `502` LLM gateway failure, `503` assistant not configured.

[api.http](api.http) exercises every endpoint (JetBrains HTTP client format, runnable from IntelliJ).

## Chat

Open [http://localhost:8080/](http://localhost:8080/) for a minimal chat page. Type a message and the assistant replies; the conversation has memory, so you can refer back ("mark it done") without repeating task ids.

Memory is per session: the first message mints a `sessionId` (returned in the response and reused by the page for follow-ups); omitting it — or reloading the page — starts a fresh conversation. Send `sessionId` yourself when calling `/api/v1/chat` directly to continue a conversation.

**Conversation caveats**: history is in memory only (lost on restart) and bounded to the most recent turns, so a very long conversation forgets its earliest messages.

**Storage caveat**: tasks live in memory only — data is lost on restart; persistence is a future change.
