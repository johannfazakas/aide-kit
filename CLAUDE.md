# aide-kit — notes for Claude

Personal aid application: Ktor backend where AI agents help with daily life, starting with task management (REST API + conversational assistant). See README.md for architecture, tech stack, and run instructions.

## Working on this repo

- Development follows the OpenSpec workflow (`/opsx:*` commands): non-trivial changes go through a change proposal under `openspec/changes/`, and `openspec/specs/` holds the current requirements. Sync and archive changes when done.
- Keep README.md accurate, simple, and concise. When behavior, architecture, or commands change, update it as part of the same change — it should always reflect reality without growing bloated.
- Layering is `routes` → `service` → `repository`; the assistant's tools (`agent/TaskTools`) must go through `TaskService`, never the repository directly.
- The assistant has session-scoped conversation memory (in-memory, bounded window, lost on restart) and deliberately has no delete tool. Unknown client-supplied session ids are not continued — the server mints a fresh id instead.
- Unit tests use given-when-then naming; integration tests live under the package of the routes they exercise (`routes/`). Nothing in the build calls an LLM — keep it that way. `./gradlew build` stays Docker-free but requires an Android SDK (the client builds an Android app; `local.properties`/`ANDROID_HOME` point at it); `./gradlew installLocal` additionally packages both images (service via Jib, web via `client/Dockerfile`) and needs a Docker daemon.
- Code style is enforced by ktlint (`ktlint_official` + `no-unused-imports`, pinned in `gradle/libs.versions.toml`, configured in `.editorconfig`). A `PostToolUse` hook lint-checks every `.kt`/`.kts` file you edit and blocks on violations — run `./gradlew ktlintFormat` to auto-fix rather than hand-fixing style errors. CI enforces the same rules via `./gradlew build`.

## Environment

- `OPENCODE_API_KEY` — OpenCode Zen key; **required** — the service fails fast at startup without it (tests pass a dummy key; nothing in the build calls an LLM).
- `PORT` (default 7080) and `CORS_ALLOWED_ORIGINS` (default: localhost origins on any port) configure the service; the web client container serves on 7081.
- `OPENCODE_BASE_URL` — `https://opencode.ai/zen/go` for the Go subscription (owner's setup); defaults to pay-per-token `https://opencode.ai/zen`.
- Model is `glm-5.2`, defined in `agent/AssistantModel.kt`; custom `LLModel`s must declare the `Tools` capability or Koog silently degrades the agent to a plain chatbot.

## Planned directions

A current-date tool (agent currently asks instead of resolving "tomorrow"), agent-side deletion, persistent storage (tasks and conversations).
