# Design — add-conversation-memory

## Context

Today the chat flow is a single line: `ChatRoutes.kt` calls `aiAgent(request.message, model = ...)` with one string and no prior turns. The system prompt (`Assistant.kt:23-24`) explicitly tells the model conversations are stateless and to repeat task ids so the user can refer back. The agent reaches `TaskService` through `TaskTools`. There is no store for history and no way to chat interactively — only `api.http` request snippets.

```
   POST /chat {sessionId?, message}
        │
        ▼
   ConversationStore.get(sessionId)         (in-memory, bounded window)
        │  history + userMessage
        ▼
   agent runs over full conversation ──▶ may call TaskTools ──▶ reply
        │  history + reply, store.put
        ▼
   ChatResponse {sessionId, reply}
```

## Goals / Non-Goals

**Goals:**

- Session-scoped memory so the assistant can resolve follow-up references within a conversation.
- Server mints a `sessionId` when the client doesn't supply one; the client continues by echoing it back.
- Keep the assistant's tool access intact across turns.
- A single static `chat.html` that lets the owner actually converse, same-origin (no CORS).
- Bound per-session history so memory and token cost stay controlled.
- Preserve the build's "nothing calls an LLM" rule — memory logic is unit-testable without the LLM.

**Non-Goals:**

- No persistence (in-memory only; lost on restart).
- No authentication or per-user isolation — sessions are opaque ids, single-user tool.
- No streaming responses; request/response stays turn-based.
- No conversation listing/management API (no "list my sessions", no delete).
- No current-date resolution (still a separate planned change).

## Decisions

### Session identity: server-minted, client-echoed opaque id

First request with no `sessionId` → server generates one (UUID) and returns it in `ChatResponse`. Client sends it back on later turns. New conversation = omit the id. *Alternative — single global conversation*: simpler but only one thread and needs an explicit reset endpoint; rejected because sessions are the planned direction and cost almost nothing. *Alternative — client-generated ids*: lets the client collide/forge ids; server-minted is safer and no harder.

### Storage: in-memory, thread-safe, bounded window

A `ConversationStore` holding `Map<String, List<Message>>` in a `ConcurrentHashMap`, updated per turn. Mirrors `InMemoryTaskRepository`'s in-memory approach. Layering: history is the chat capability's own concern, so the store sits behind the chat route the way the repository sits behind the task service — final placement (standalone store vs. repository+service pair) is an implementation detail, but tools must still reach `TaskService`, never the store.

History is bounded to the last N turns (window applied when assembling agent input). This caps token growth and memory. N is a small constant chosen at implementation (e.g. keep the last ~20 messages); the window trims oldest-first. Trade-off: very long conversations lose their earliest context — acceptable for now and far better than unbounded growth. Note it in README.

### Feeding history to the agent — spike resolved: assembled transcript

`aiAgent(input, model)` (koog-ktor `Agents.kt`) builds the agent from the **install-time** prompt in the Koog plugin and calls `run(input, null)`. Inspecting the 1.1.1 API:

- `AIAgent.run(Input, String)` — the second `String` is an id argument (passed `null` today), not a history hook.
- The convenience `aiAgent` overloads accept no per-request `Prompt`; the prompt lives in `KoogAgentsConfig.AgentConfig.prompt`, fixed at `install(Koog)` time, and `agentConfig(model)` is `internal`.
- A native message-role history would require bypassing the helper and constructing `GraphAIAgent` directly against `plugin.promptExecutor` / `agentConfig(model)` / `agentFeatures` — coupling to internal, beta-versioned APIs.
- Tools come from `plugin.agentConfig.toolRegistry`, independent of `input`, so tool use survives regardless of how input is built.

**Chosen path — assembled transcript.** Format the recent conversation into the single `input` string (labelled `User:` / `Assistant:` turns, current message last) and keep the existing convenience call and tool registry untouched. This avoids coupling to internal/beta APIs, and tool use is guaranteed because the registry is unchanged. Trade-off accepted: prior turns are flattened to their natural-language text — the assistant's earlier tool calls aren't replayed as structured records, but ids/titles it stated in prior replies carry forward in the text. Live end-to-end confirmation that a `TaskTools` call still fires mid-conversation happens at task 5.1 (needs the LLM key; not part of the build).

### System prompt rewrite

Remove the lines telling the model conversations are stateless and to repeat task ids (`Assistant.kt:23-24`). With memory, the model can rely on prior turns; keeping the instruction would make it over-verbose. The clarification/relative-date guidance stays.

### Chat UI: one static file, same origin

`src/main/resources/chat.html` — a text box, a running message list, vanilla `fetch()` POSTing to `/api/v1/chat`, holding the returned `sessionId` in a JS variable and resending it. Served by a Ktor static route at `/`. No new dependency (static content ships with `ktor-server-core`), no CORS (same origin), no build step. A "new conversation" is a page reload (drops the in-page id).

## Risks / Trade-offs

- [Koog has no native multi-turn entry point] → Fall back to the assembled-transcript path; the spike (task 1) decides before other work depends on it.
- [Chosen history mechanism silently disables tool use] → Explicitly verify a tool call still fires within a multi-turn conversation during the spike, not just that history is seen.
- [Unbounded history inflates tokens/memory/latency] → Bounded recent-turn window, oldest-first trim; documented context-loss caveat.
- [In-memory store lost on restart] → Accepted; persistence is a stated non-goal and future change. README notes it alongside the existing task-storage caveat.
- [Concurrent turns on one session interleave] → `ConcurrentHashMap` plus append discipline; single-user tool makes contention unlikely, but the store must not lose writes.
- [System prompt rewrite regresses task-id clarity] → Ids still appear naturally in tool results and replies; only the forced repetition is removed.

## Open Questions

- Exact history-window size (N) — pick a small default at implementation; not worth blocking on.
- Whether the store is a bare component or a repository+service pair — decide by what reads cleanest against the existing layering; no behavioral impact.
