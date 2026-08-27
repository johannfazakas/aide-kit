# Tasks: add-current-date-tool

## 1. Date tool

- [x] 1.1 Add `DateTools` in `service/src/main/kotlin/ro/jf/ai/assistant/agent/` — a Koog `ToolSet` with a `currentDate` tool taking no arguments and returning JSON `{"date": "yyyy-MM-dd", "dayOfWeek": "<DAY>"}`; accept a `Clock` (default `Clock.System`) and derive the date via `TimeZone.currentSystemDefault()`.
- [x] 1.2 Register `DateTools` alongside `TaskTools` in `installAssistant` (`Assistant.kt`).

## 2. Prompt

- [x] 2.1 Update `ASSISTANT_SYSTEM_PROMPT`: remove the "you do not know the current date" instruction; instruct the assistant to resolve relative dates via the current-date tool, never assume a date without it, and still ask when an expression stays ambiguous (e.g., "sometime next week").

## 3. Tests

- [x] 3.1 Unit tests for `DateTools` with a fixed `Clock` (given-when-then naming): exact ISO date, correct day of week, date/zone boundary sanity.
- [x] 3.2 Review existing chat/agent tests for assertions tied to the old "always ask for the date" behavior and align them; `./gradlew build` stays green, LLM-free, and Docker-free.

## 4. Documentation and verification

- [x] 4.1 Update `CLAUDE.md` (planned directions) and `README.md` if it mentions the date limitation.
- [x] 4.2 Live verification against the running service: "add a task due tomorrow" creates the task with tomorrow's date; a weekday-relative request ("next Friday") resolves correctly; "sometime next week" still gets a clarifying question.
