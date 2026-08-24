# Design — fix-agent-tool-loop

## Context

`ChatRoutes.kt` calls koog-ktor's `aiAgent(input, model)` convenience, which runs Koog's default `singleRunStrategy()` (`agents-core` 1.1.1, `AIAgentSimpleStrategies.kt`). Its graph:

```
start → callLLM
callLLM        → executeTool     onToolCalls        ← tool edge first
callLLM        → finish          onTextMessage
executeTool    → sendToolResult
sendToolResult → finish          onTextMessage      ← text edge first (the bug surface)
sendToolResult → executeTool     onToolCalls
```

`onTextMessage` matches a response containing *any* text part; `onToolCalls` matches *any* tool-call part; a mixed response (narration + tool call) matches both, and declaration order decides. After tool results the finish edge wins, so glm-5.2's habit of narrating ("let me mark it done") ends the run — the observed live failure: `updateTask` never executed in any list-then-update flow, while first-response tool calls (create, list) always worked. Reproduced stateless, so unrelated to conversation memory.

Verified against Koog 1.1.1 sources (Gradle cache):

- `RoutingContext.aiAgent(strategy: AIAgentGraphStrategy<Input, Output>, model, input)` is **public** (`koog-ktor` `Agents.kt`) and internally reuses `plugin.agentConfig(model)`, the install-time prompt, tool registry, and features. The add-conversation-memory spike's concern about internal APIs applies only to building `GraphAIAgent` by hand — not to supplying a custom strategy.
- `chatAgentStrategy()` (`agents-ext`, shipped inside `agents-core`) exists but is wrong for us: it nags the model to never answer in plain text, expects an `__exit__` tool to end the chat, and has the same finish-on-text-first ordering after tool results.
- `structuredOutputWithToolsStrategy` demonstrates the correct idiom: terminal edges guarded by `onCondition { msg -> msg.parts.none { it is MessagePart.Tool.Call } }` — finish only when the response contains no tool calls.

## Goals / Non-Goals

**Goals:**

- "Mark it as done" flows complete: sequential tool rounds (list/get → update) execute within one request.
- Narration accompanying a tool call never ends the run before the tool executes.
- An assistant-owned strategy graph as the extension point for future orchestration needs.
- Keep the install-time prompt/tool wiring and the build's no-LLM rule intact.

**Non-Goals:**

- No ReAct-style reasoning loops, structured output, or history compression — plain request/response stays.
- No forced tool use (unlike `chatAgentStrategy`): plain-text answers to plain questions remain first-class.
- No unbounded agent loops — the strategy adds no retry/nudge cycles in this change.
- No change to the conversation-memory mechanism (assembled transcript stays as is).

## Decisions

### Custom strategy graph over prompt-only fix or chatAgentStrategy

A new `assistantStrategy()` in `agent/` using Koog's public `strategy { }` DSL — `single_run`'s exact graph with one delta: after `sendToolResult`, the tool-call edge is declared before the finish-on-text edge.

```
start → callLLM
callLLM        → executeTool  onToolCalls
callLLM        → finish       onTextMessage
executeTool    → sendToolResult
sendToolResult → executeTool  onToolCalls           ← tool edge now first (single_run has finish first)
sendToolResult → finish       onTextMessage
```

Edge resolution is documented first-match declaration order (`AIAgentNode.resolveEdge`), so with the tool edge first, mixed responses (narration + tool call) execute their tools and only genuinely tool-free responses finish the run. Stock `onToolCalls`/`onTextMessage` conditions are kept — no custom predicates; the deliberate trade-off is that correctness rests on edge declaration order (as `single_run`'s own behavior already does), which the strategy's minimal size keeps easy to eyeball. *Alternative — `chatAgentStrategy()`*: rejected; it forbids plain-text answers, needs an `__exit__` tool, and repeats the ordering flaw. *Alternative — prompt-only fix*: rejected as primary because it leaves the deterministic dropped-tool-call defect in place; kept as complementary hardening. Deliberate trade-off: the strategy is deterministic about the failure mode we proved (mixed responses); the prompt line covers the one we couldn't rule out (pure narration with no tool call), since no graph can execute a call that isn't there.

### Wire via the public aiAgent(strategy, model, input) overload

`ChatRoutes` switches to `aiAgent(assistantStrategy(), AssistantModel.GLM_5_2, input)`. Everything else — Koog plugin install, prompt, `TaskTools` registry — is untouched, and tools keep flowing through `TaskService`. *Alternative — constructing `GraphAIAgent` directly*: needless coupling; the public overload already reuses the plugin config.

### System-prompt hardening as defense in depth

Add: when the assistant decides on an action, it must call the tool in the same response — never reply that it is *about to* do something — and continue until the user's request is fully carried out. This targets the pure-narration variant the strategy cannot fix.

### Soften updateTask's fetch-first description

Keep the full-replace semantics and the advice to fetch unknown values, but stop mandating a fetch when the model already has current values (e.g. from the create result or an earlier list in the same run). Fewer forced second rounds; the loop fix makes the remaining ones safe.

### Verification approach

The strategy graph is plain code, unit-testable only shallowly (Koog offers no lightweight fake executor worth adopting here) — so its correctness is asserted live: rerun the exact failing scenario (create by chat, "mark it as done" by reference, assert `completed: true` via the REST API, `updateTask` visible in logs). The build keeps calling no LLM; existing route tests must stay green.

## Risks / Trade-offs

- [glm-5.2 emits pure narration with no tool call after results] → Strategy can't fix that variant; the prompt rule targets it. If the live check still fails, escalate to a nudge node (à la `chatAgentStrategy`'s feedback loop, but bounded to one retry) in a follow-up.
- [Tool-calls-first edges could loop indefinitely with a pathological model] → Koog's agent config carries a max-iterations bound; the graph adds no cycle that `single_run` didn't already have.
- [`agents-ext` idioms are beta-versioned] → We copy the two-line edge condition, not depend on ext entry points; pinned Koog version in the catalog.
- [Softer updateTask description lets the model update with stale values] → Full-replace semantics unchanged and single-user scale; the description still recommends fetching unknown values.

## Open Questions

- None blocking. Whether a bounded nudge node is needed is decided by the live check, not up front.
