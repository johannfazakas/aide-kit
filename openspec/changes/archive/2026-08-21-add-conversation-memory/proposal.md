# Add conversation memory

## Why

The assistant is stateless: every chat request is an independent turn, so the model cannot follow up ("mark it as done") and the system prompt has to compensate by repeating task ids in every reply. This makes real conversation impossible. There is also no way to actually chat with the assistant during development — only raw request snippets. Adding in-memory, session-scoped history and a minimal chat page turns the assistant into something you can genuinely hold a conversation with.

## What Changes

- Introduce session-scoped conversation memory: the chat endpoint retains per-session history in memory (no persistence yet), so the assistant sees prior turns and can resolve references like "it" or "that task".
- `POST /api/v1/chat` accepts an optional `sessionId`; when absent the server mints a new one and returns it. The response includes the `sessionId` so the client can continue the conversation. Omitting the id starts a fresh conversation.
- Bound each session's history to a recent window so long conversations don't grow memory or token usage without limit.
- Rewrite the assistant system prompt: remove the "conversations are stateless / repeat task ids" instruction, which becomes counterproductive once the model can see history.
- Add a minimal static chat page (`chat.html`) served by the app: a text box, a running message list, and `fetch()` calls to the existing endpoint — the simplest thing that lets the owner actually chat with the assistant.
- The assistant must still invoke `TaskTools` within a multi-turn conversation; memory changes how input is assembled, not the agent's tool access.

## Capabilities

### New Capabilities

_None — this extends the existing chat capability rather than introducing a new one._

### Modified Capabilities

- `assistant-chat`: the "Stateless conversations" requirement is replaced by session-scoped conversation memory; the chat endpoint contract gains an optional request `sessionId` and a response `sessionId`; a new requirement adds the static chat web UI; a new requirement bounds retained history.

## Impact

- `transfer/ChatRequest.kt`, `transfer/ChatResponse.kt`: add `sessionId` (optional in, present out) — backward-compatible.
- `routes/ChatRoutes.kt`: resolve/mint session, load and append history, run the agent over the conversation, persist the reply.
- New in-memory conversation store component (mirrors `InMemoryTaskRepository`), thread-safe, with a bounded history window.
- `agent/Assistant.kt`: system prompt rewrite (drop the statelessness paragraph).
- `src/main/resources/chat.html` + a Ktor static route serving it.
- `openspec/specs/assistant-chat/spec.md`: delta with modified + added requirements.
- `README.md`: document the chat page and the session behavior.
- Open technical item for design: confirm how Koog's agent API accepts multi-turn history while keeping tool use available.
