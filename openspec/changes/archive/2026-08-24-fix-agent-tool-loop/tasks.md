# Tasks — fix-agent-tool-loop

## 1. Custom strategy

- [x] 1.1 Add `agent/AssistantStrategy.kt`: `assistantStrategy()` built with Koog's `strategy { }` DSL — `single_run`'s four nodes, tool-call edges decisive, finish edges guarded by "response has no tool-call parts" (per design)
- [x] 1.2 Switch `ChatRoutes` to the public `aiAgent(assistantStrategy(), AssistantModel.GLM_5_2, input)` overload; confirm degraded-mode (503) and blank-message (400) behavior unchanged

## 2. Prompt and tool description hardening

- [x] 2.1 Extend the system prompt in `Assistant.kt`: call the tool in the same response as the decision — never announce an action without performing it; continue until the request is fully done
- [x] 2.2 Soften `updateTask`'s description in `TaskTools.kt`: full-replace semantics stay, fetching is advised only when current values are unknown

## 3. Verification & docs

- [x] 3.1 Run `./gradlew build` — lint and all tests green, still no LLM calls in the build
- [x] 3.2 Live check with `OPENCODE_API_KEY`: create a task via chat, then "mark it as done" by reference in the same session; assert the task's `completed` is `true` via `GET /api/v1/tasks` and `updateTask` appears in the agent logs. Also confirm a plain question still gets a plain answer — DONE: task flipped to completed, `updateTask` executed (directly, no fetch round needed), plain answers unaffected
- [x] 3.3 If the live check still fails on pure narration (no tool call emitted), record findings in design.md and scope the bounded nudge-node follow-up — do not expand this change — NOT NEEDED: live check passed
- [x] 3.4 Update `README.md` architecture note if the strategy swap changes anything user-visible (expected: no doc change beyond the agent description mentioning the custom strategy)
