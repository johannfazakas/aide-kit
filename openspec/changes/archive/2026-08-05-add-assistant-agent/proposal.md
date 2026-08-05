# Proposal: add-assistant-agent

## Why

The app's vision is a personal aid application where AI agents help manage aspects of daily life — but so far it only exposes a plain REST API. This change adds the first AI agent: a conversational assistant, built with JetBrains Koog, that can manage the user's tasks through natural language ("add a task to buy groceries", "what's on my list for the work category?") by calling tools that wrap the existing task service.

## What Changes

- Add the Koog agent framework via the `koog-ktor` plugin, configured against OpenCode Zen (an OpenAI-compatible LLM gateway) using a GLM model (`glm-5.2`), with the API key supplied via environment/configuration.
- Define a custom `LLModel` for `glm-5.2` (not in Koog's built-in catalog) that explicitly declares tool-calling capability.
- Add a `TaskTools` tool set exposing task operations to the agent: list (with optional category filter), get by id, create, and update (which covers completing a task). Deliberately **no delete tool** in this iteration.
- Add a chat endpoint `POST /api/v1/chat` accepting a user message and returning the agent's reply. Stateless: each request is an independent conversation with no memory of previous exchanges.
- Add a system prompt framing the agent as a broad personal assistant whose current capability is task management, instructed to ask clarifying questions when instructions are ambiguous (e.g., relative dates like "tomorrow", since the agent has no current-date awareness yet).
- Add unit tests for the tool layer (no LLM required) and keep agent wiring thin; no real-LLM tests in the build.
- Extend `api.http` with chat endpoint examples and update the README.

## Capabilities

### New Capabilities

- `assistant-chat`: conversational assistant over a chat endpoint — request/response contract, statelessness, agent behavior (tool-backed task management, clarification on ambiguity), and error semantics (missing/blank message, LLM provider not configured).

### Modified Capabilities

<!-- none — the task-management REST API and its requirements are unchanged; the agent reuses the existing service layer -->

## Impact

- **Code**: new `agent` package (`ro.jf.ai.assistant.agent`) containing the Koog plugin installation/configuration, model definition, system prompt, `TaskTools`, and chat route; `Application.kt` modified to install the plugin and register the route.
- **APIs**: new public HTTP surface `POST /api/v1/chat` (JSON). Existing `/api/v1/tasks` endpoints are untouched.
- **Dependencies**: `ai.koog:koog-ktor` (Koog 1.1.x) added to the version catalog; transitively brings Koog agents core and LLM clients.
- **Configuration**: new required secret `OPENCODE_API_KEY` (environment variable) for the OpenCode Zen gateway; without it the app still starts and serves the REST API, but chat requests fail with a clear error.
- **Cost/externality**: chat requests call an external paid LLM API (OpenCode Zen, ~$1.40/M input tokens for GLM 5.2); no LLM calls happen at build/test time.
- **Docs/tooling**: README section on the assistant and its configuration; `api.http` chat examples.
