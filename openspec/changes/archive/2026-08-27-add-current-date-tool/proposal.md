## Why

The assistant cannot resolve relative dates: its system prompt tells it that it does not know the current date, so requests like "add a task due tomorrow" bounce back with a clarifying question for the exact date. This is the most frequent friction in daily use — relative dates are the natural way to talk about tasks — and it was already flagged as the first planned direction for the assistant.

## What Changes

- Add a current-date tool to the assistant's toolset, returning today's date (server-local) so the agent can resolve relative expressions like "tomorrow", "in three days", or "next Friday" into concrete ISO dates for task due dates.
- Update the assistant's system prompt: replace the "you do not know the current date, ask the user" instruction with guidance to call the date tool when a relative date needs resolving, and to still ask when an expression stays ambiguous (e.g., "sometime next week").
- The existing clarification behavior for genuinely ambiguous instructions is unchanged; only date resolution moves from "always ask" to "resolve via tool".

## Capabilities

### New Capabilities

None — this extends the existing assistant capability.

### Modified Capabilities

- `assistant-chat`: the "Clarification on ambiguous instructions" requirement changes — relative dates are no longer a mandated clarification case. A new requirement covers current-date awareness: the assistant resolves relative dates through a tool instead of asking, while still clarifying truly ambiguous date expressions.

## Impact

- `service/src/main/kotlin/ro/jf/ai/assistant/agent/` — new date tool (alongside `TaskTools`), registered in `Assistant.kt`; system prompt text updated.
- Unit tests for the new tool; existing chat integration tests reviewed for the removed "always ask for dates" behavior. Nothing in the build calls an LLM — that stays true.
- No API, client, or deployment changes; clients are unaffected.
