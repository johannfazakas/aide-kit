# aide-kit — notes for Claude

Personal aid application: Ktor backend where AI agents help with daily life, starting with task management (REST API + conversational assistant). See README.md for architecture, tech stack, and run instructions.

## Working on this repo

- Development follows the OpenSpec workflow (`/opsx:*` commands): non-trivial changes go through a change proposal under `openspec/changes/`, and `openspec/specs/` holds the current requirements. Sync and archive changes when done.
- Keep README.md accurate, simple, and concise. When behavior, architecture, or commands change, update it as part of the same change — it should always reflect reality without growing bloated.
- Layering is `routes` → `service` → `repository`; the assistant's tools (`agent/TaskTools`) must go through `TaskService`, never the repository directly.
- The assistant is stateless by design (no conversation memory yet) and deliberately has no delete tool.
- Unit tests use given-when-then naming; integration tests live under the package of the routes they exercise (`routes/`). Nothing in the build calls an LLM — keep it that way.

## Environment

- `OPENCODE_API_KEY` — OpenCode Zen key; without it the app runs in degraded mode (task API works, chat 503s).
- `OPENCODE_BASE_URL` — `https://opencode.ai/zen/go` for the Go subscription (owner's setup); defaults to pay-per-token `https://opencode.ai/zen`.
- Model is `glm-5.2`, defined in `agent/AssistantModel.kt`; custom `LLModel`s must declare the `Tools` capability or Koog silently degrades the agent to a plain chatbot.

## Planned directions

Conversation memory (sessions), a current-date tool (agent currently asks instead of resolving "tomorrow"), agent-side deletion, persistent storage, mobile client.
