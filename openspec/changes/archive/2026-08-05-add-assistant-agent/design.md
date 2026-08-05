# Design: add-assistant-agent

## Context

The app is a Ktor 3 / Kotlin 2.2 service with a clean `routes → service → repository` layering and an in-memory task store. This change introduces the first AI agent using JetBrains Koog (1.1.x), reachable through a new chat endpoint. The agent runs in-process and interacts with tasks by calling the existing `TaskService` directly — no HTTP self-calls, no duplicated validation.

The LLM provider is OpenCode Zen, an OpenAI-compatible gateway (`https://opencode.ai/zen`), serving the GLM model family. Koog has no native OpenCode provider, but its clients accept custom base URLs.

```
POST /api/v1/chat {message} ──▶ Koog agent (glm-5.2 via OpenCode Zen)
                                   │
                                   └─ TaskTools ──▶ TaskService ──▶ TaskRepository
```

## Goals / Non-Goals

**Goals:**
- A working conversational assistant that can list, inspect, create, update, and complete tasks via natural language.
- Idiomatic Ktor integration via the `koog-ktor` plugin (`install(Koog)`).
- Framing that scales: the assistant is a general personal aid; task management is its first tool set of several to come.
- Keep the existing REST API and layering untouched.

**Non-Goals:**
- Conversation memory/sessions (stateless v1; session history is the obvious next change).
- Task deletion through the agent (explicitly deferred).
- Current-date awareness (agent asks for clarification instead; a date tool comes later).
- Streaming responses (SSE).
- Real-LLM tests in the build.

## Decisions

### 1. `koog-ktor` plugin over plain `AIAgent` construction
Use `install(Koog) { llm { ... } }` in the application module and the plugin's route-level agent API for the chat endpoint. Rationale: idiomatic Ktor, config loading from environment/`application.yaml` built in, and it is the direction JetBrains is pushing for Ktor apps. Alternative — constructing `AIAgent` manually like `TaskService` — is more transparent but duplicates what the plugin provides; rejected by preference. The plugin artifact is `ai.koog:koog-ktor` (1.1.1-beta, matching Koog 1.1.1).

### 2. OpenCode Zen via an OpenAI-compatible client with custom base URL
Configure Koog's OpenAI client with `baseUrl = https://opencode.ai/zen` and API key from `OPENCODE_API_KEY`. Caveat verified against Koog source: if the OpenAI client insists on OpenAI's Responses API dialect for this Koog version, fall back to Koog's OpenRouter client (plain chat-completions dialect, also supports custom base URL) pointed at the same endpoint. This is a contained, implementation-time switch; both are configured in the same `llm { }` block.

### 3. Custom `LLModel` for `glm-5.2` with explicit `Tools` capability
`glm-5.2` is not in Koog's built-in model catalog, so define `LLModel(provider, id = "glm-5.2", capabilities = [Completion, Tools, Temperature, ...])`. Declaring `Tools` is load-bearing: without it Koog refuses to attach tools and the agent silently degrades to a plain chatbot. Model id kept in one place so swapping models is a one-line change.

### 4. Tools as a `ToolSet` wrapping `TaskService`
`TaskTools : ToolSet` with `@Tool`/`@LLMDescription`-annotated methods: `listTasks(category?)`, `getTask(id)`, `createTask(title, dueDate?, category?)`, `updateTask(id, ...)`. Completing a task is an update (`completed = true`) — no separate tool. No delete tool. Service exceptions (`TaskNotFoundException`, `IllegalArgumentException`) are caught and returned as tool-error strings so the LLM can self-correct (e.g., list tasks to find the right id). Tool results are serialized as compact JSON strings.

### 5. Stateless chat contract
`POST /api/v1/chat` takes `{"message": "..."}` and returns `{"reply": "..."}`. Each request is one agent run with fresh history. This sidesteps session storage entirely; "mark the second one done" style follow-ups are out of scope until session memory lands.

### 6. System prompt: broad assistant, clarify when unsure
The prompt frames the agent as a general personal assistant whose currently available capability is task management, and instructs it to ask a clarifying question (in its reply) rather than guess when instructions are ambiguous — notably relative dates ("tomorrow"), since it has no current-date awareness yet.

### 7. Degraded mode without an API key
If `OPENCODE_API_KEY` is absent the app still starts and serves the REST API; the chat endpoint responds `503` with a clear error body instead of failing at startup. Rationale: task API consumers shouldn't be hostage to LLM configuration.

### 8. Agent failures surface as 502 with the cause
Koog wraps run-time failures (including LLM client errors) in `AIAgentException`; without handling, these surface as empty `500`s. StatusPages maps `AIAgentException` and `LLMClientException` to `502 Bad Gateway` with the root-cause message in the error body — the upstream LLM is effectively a gateway dependency. Added after live verification surfaced an empty `500` on a rejected API key.

### 9. Testing strategy
Unit tests (given-when-then) for `TaskTools` against a real `TaskService` + in-memory repo — no LLM involved. Chat endpoint integration test covers the no-API-key `503` path and request validation (`400` on blank message). The agent loop itself is not tested against a live LLM in the build; manual verification goes through `api.http`.

## Risks / Trade-offs

- [koog-ktor is beta (1.1.1-beta)] → Wiring is confined to the application module and one route; falling back to plain `AIAgent` construction touches nothing else.
- [OpenCode Zen dialect mismatch with Koog's OpenAI client] → Verified fallback path via OpenRouter client (Decision 2); checked during the first implementation task, not discovered late.
- [GLM tool-calling quality unknown for this use] → Tools have rich `@LLMDescription`s; model id is centralized for cheap swaps to other Zen models.
- [Stateless chat can feel dumb ("which one?" answered into the void)] → Accepted for v1; system prompt tells the agent to include enough context (task ids/titles) in replies for the user to follow up explicitly.
- [Paid external API] → No LLM calls at build/test time; chat is the only call site.

## Open Questions

- None blocking. The OpenAI-vs-OpenRouter client choice (Decision 2) is resolved by a five-minute check during implementation.
