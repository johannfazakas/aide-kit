# Fix agent tool loop

## Why

The live check of conversation memory exposed that the assistant cannot complete any flow needing a second, sequential tool call: it lists tasks to find the id, then replies "let me mark it as done" and stops — `updateTask` never executes, so "mark it as done" silently does nothing. Root cause: glm-5.2 narrates after receiving tool results, and Koog's default `single_run` strategy finishes on any text part at that point (its finish-on-text edge is declared before the tool-call edge), so narration ends the run — dropping any tool call that accompanies or should follow it.

## What Changes

- Replace the default `single_run` strategy with an assistant-owned strategy graph (same LLM → tools → results loop, built with Koog's public strategy DSL) whose edges finish only when a response contains no tool calls — tool calls always win over accompanying narration. This also establishes the home for future custom orchestration.
- Pass the custom strategy through koog-ktor's public `aiAgent(strategy, model, input)` overload, keeping the install-time system prompt, tool registry, and features (no internal APIs — the earlier spike's concern no longer applies).
- Harden the system prompt: never announce an action without calling the tool in the same response; keep going until the request is fully done.
- Revisit `updateTask`'s "fetch the task first" tool description — kept correct but no longer fatal once sequential rounds work; soften wording so the model may update directly when it already knows the current field values.
- Verify live that "create a task, then mark it as done by reference" completes end-to-end (task flips to `completed: true`), alongside the existing no-LLM build guarantee.

## Capabilities

### New Capabilities

_None — this fixes the existing assistant capability rather than introducing a new one._

### Modified Capabilities

- `assistant-chat`: add a requirement that the assistant completes multi-step tool flows within a single request — sequential tool calls after tool results are executed, and narration accompanying a tool call must not end the run before the action happens.

## Impact

- New `agent/AssistantStrategy.kt` (or similar): the custom strategy graph.
- `routes/ChatRoutes.kt`: call the `aiAgent(strategy, model, input)` overload instead of `aiAgent(input, model)`.
- `agent/Assistant.kt`: system-prompt hardening.
- `agent/TaskTools.kt`: `updateTask` description wording.
- `openspec/specs/assistant-chat/spec.md`: delta with the added requirement.
- No new dependencies (`agents-ext` strategies ship in `agents-core`, already on the classpath); build stays LLM-free.
