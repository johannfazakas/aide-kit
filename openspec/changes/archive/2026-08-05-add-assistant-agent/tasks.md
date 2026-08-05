# Tasks: add-assistant-agent

## 1. Dependencies & wiring

- [x] 1.1 Add `koog-ktor` (Koog 1.1.x) to `gradle/libs.versions.toml` and `build.gradle.kts`; verify the project compiles
- [x] 1.2 Install the `Koog` plugin in `Application.kt`, configured from `OPENCODE_API_KEY` against the OpenCode Zen base URL; resolve Decision 2 (OpenAI client with custom baseUrl vs OpenRouter client) with a real call check — resolved via Koog 1.1.1 source: default params use the chat-completions path and the base-URL path prefix is preserved; live confirmation happens in 4.3
- [x] 1.3 Define the custom `LLModel` for `glm-5.2` with explicit `Tools` capability in the `agent` package

## 2. Tools

- [x] 2.1 Implement `TaskTools : ToolSet` wrapping `TaskService`: `listTasks(category?)`, `getTask(id)`, `createTask(title, dueDate?, category?)`, `updateTask(id, title, dueDate?, category?, completed)` with `@LLMDescription`s; no delete tool
- [x] 2.2 Map domain exceptions (`TaskNotFoundException`, `IllegalArgumentException`) to tool-error strings; serialize tool results as compact JSON
- [x] 2.3 Unit tests for `TaskTools` (given-when-then) against `TaskService` + in-memory repository, covering list/get/create/update/complete and error mapping

## 3. Chat endpoint

- [x] 3.1 Implement `POST /api/v1/chat` running a stateless agent per request with the system prompt (broad personal assistant, task tools, clarify-on-ambiguity), returning `{"reply": ...}`
- [x] 3.2 Validate the request: `400` with error body on missing/blank `message`
- [x] 3.3 Degraded mode: without `OPENCODE_API_KEY` the app starts, task API works, chat returns `503` with a clear error body
- [x] 3.4 Integration tests via `testApplication`: blank-message `400`, missing-key `503`, task API unaffected (plus a fake-key startup test exercising the Koog install and tool reflection)
- [x] 3.5 Map agent/LLM failures (`AIAgentException`, `LLMClientException`) to `502 Bad Gateway` with the root-cause message via StatusPages, with an integration test

## 4. Docs & manual verification

- [x] 4.1 Extend `api.http` with chat examples (task creation, listing, completion, ambiguous-date clarification, delete refusal)
- [x] 4.2 Update README: assistant overview, `OPENCODE_API_KEY` configuration, model choice and how to swap it
- [x] 4.4 Make the gateway base URL configurable via `OPENCODE_BASE_URL` (default `https://opencode.ai/zen`), supporting the opencode Go subscription endpoint (`https://opencode.ai/zen/go`)
- [x] 4.3 Manual end-to-end check against OpenCode Zen using `api.http` scenarios; confirm tool calls fire (not plain-chatbot degradation) — verified live against the Go endpoint: create/list/complete mutate the store through tools, ambiguous dates trigger clarification, deletion is refused; hardened listTasks/system prompt against category guessing found during the check
