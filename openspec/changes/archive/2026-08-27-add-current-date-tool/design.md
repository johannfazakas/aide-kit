## Context

The agent's toolset lives in `agent/TaskTools.kt` (a Koog `ToolSet` registered in `Assistant.kt` via `registerTools`). The system prompt (`ASSISTANT_SYSTEM_PROMPT` in `Assistant.kt`) currently states the assistant does not know the current date and must ask instead of resolving relative expressions. Task due dates flow as ISO-8601 `yyyy-MM-dd` strings through the tools and the `kotlinx-datetime` `LocalDate` type. Nothing in the build calls an LLM; tool logic is unit-testable in isolation.

## Goals / Non-Goals

**Goals:**
- The assistant resolves relative date expressions ("tomorrow", "in 3 days", "next Friday") into concrete due dates without asking the user.
- Deterministic, unit-testable tool logic; the LLM does the language interpretation, the tool only supplies the anchor date.
- Keep the clarification behavior for genuinely ambiguous expressions.

**Non-Goals:**
- No date *arithmetic* tool (parsing "next Friday" server-side): the model is good at calendar arithmetic once it knows today's date and weekday; a parser would duplicate that poorly.
- No per-user timezone handling; the service uses its own zone (single-owner, single-server app). Revisit if the app ever serves users in other timezones.
- No client changes.

## Decisions

- **Tool shape**: a new `DateTools` `ToolSet` with a single no-argument tool `currentDate` returning JSON `{"date": "yyyy-MM-dd", "dayOfWeek": "WEDNESDAY"}`. The day of week is included because weekday-relative expressions ("next Friday") need it and deriving it is exactly the kind of arithmetic the model gets wrong.
  - *Alternative considered*: folding the tool into `TaskTools` — rejected; `TaskTools` is task-CRUD backed by `TaskService`, the date tool has no service dependency and is a separate concern.
  - *Alternative considered*: injecting the date into the system prompt per request — rejected; the prompt is registered once at install time in the Koog plugin, not per request, and a tool keeps the value fresh for long-lived sessions spanning midnight.
- **Clock injection**: `DateTools(clock: Clock = Clock.System)` with the date derived via `TimeZone.currentSystemDefault()`. Tests pass a fixed `Clock` to assert exact output (given-when-then naming).
- **Prompt update**: replace the "you do not know the current date" sentences with: resolve relative dates by calling the current-date tool, compute the target date yourself, and still ask when the expression is ambiguous (e.g., "sometime next week", "later"). Never invent a date without the tool.
- **Registration**: `registerTools { tools(TaskTools(...).asTools()); tools(DateTools().asTools()) }` in `installAssistant`.

## Risks / Trade-offs

- [Model may still miscompute weekday offsets] → the tool returns `dayOfWeek` alongside the date, minimizing the arithmetic left to the model; live verification exercises "tomorrow" and a weekday-relative case.
- [Server timezone differs from user's near midnight] → accepted for a single-owner LAN app; noted as a non-goal.
- [Existing tests asserting the "ask for the date" behavior may break] → review chat-related tests during apply and align them with the new prompt.

## Open Questions

None.
