# Tasks — add-conversation-memory

## 1. Koog multi-turn spike (resolve first)

- [x] 1.1 Determine how Koog's agent API accepts multi-turn history: check for a native message-history entry point; if none, plan the assembled-transcript fallback
- [x] 1.2 Confirm the chosen path keeps tool use active — verify a `TaskTools` call still fires within a multi-turn conversation, not just that history is seen. Record the decision in design.md

## 2. Conversation store

- [x] 2.1 Add an in-memory, thread-safe conversation store keyed by session id (mirrors `InMemoryTaskRepository`), holding per-session message history
- [x] 2.2 Apply a bounded recent-turn window (small constant N), trimming oldest-first when exceeded
- [x] 2.3 Unit-test the store with given-when-then: append/retrieve, session isolation, id minting, and window trimming — no LLM involved

## 3. Chat endpoint with sessions

- [x] 3.1 Add optional `sessionId` to `ChatRequest` and required `sessionId` to `ChatResponse` (backward-compatible with existing tests)
- [x] 3.2 Update `ChatRoutes`: resolve or mint the session id, load history, append the user message, run the agent over the conversation (per task 1), append the reply, return `{sessionId, reply}`
- [x] 3.3 Rewrite the assistant system prompt in `Assistant.kt`: remove the "conversations are stateless / repeat task ids" lines; keep clarification and relative-date guidance

## 4. Chat web UI

- [x] 4.1 Add `src/main/resources/chat.html`: message input, running exchange display, `fetch()` to `/api/v1/chat`, holding the returned `sessionId` for follow-up requests
- [x] 4.2 Serve it via a Ktor static route (same origin, no new dependency, no CORS)

## 5. Verification & docs

- [~] 5.1 Run `./gradlew build` (lint + tests) and confirm green — DONE; app boots, serves chat.html at `/`, endpoint wired (503 degraded, 400 blank) — DONE. Live multi-turn conversation through the page (create a task, then refer to it without its id) — PENDING owner's `OPENCODE_API_KEY` (not available in this environment)
- [x] 5.2 Update `README.md`: document the chat page, session behavior, the bounded-history caveat, and the in-memory (lost on restart) caveat
