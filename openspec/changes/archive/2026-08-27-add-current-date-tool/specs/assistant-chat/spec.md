# Delta: assistant-chat

## ADDED Requirements

### Requirement: Current-date awareness
The assistant SHALL have a tool that returns the current date (server-local) in ISO-8601 format (`yyyy-MM-dd`) together with the day of week. The assistant's system prompt SHALL instruct it to resolve relative date expressions (such as "tomorrow", "in three days", or "next Friday") by invoking this tool and computing the concrete date, rather than asking the user, and SHALL forbid assuming a date without the tool. Expressions that remain ambiguous even with the current date known (such as "sometime next week") SHALL still be clarified with the user.

#### Scenario: Relative due date resolved
- **WHEN** the user asks for a task due "tomorrow"
- **THEN** the assistant invokes the current-date tool and creates the task with the concrete date one day after the returned date, without asking the user for a date

#### Scenario: Weekday-relative date resolved
- **WHEN** the user asks for a task due "next Friday"
- **THEN** the assistant resolves the target date from the tool's returned date and day of week and creates the task with a concrete `yyyy-MM-dd` due date

#### Scenario: Still-ambiguous date expression
- **WHEN** the user gives a date expression that the current date does not disambiguate, such as "sometime next week"
- **THEN** the reply asks a clarifying question instead of picking a date

#### Scenario: Deterministic tool output
- **WHEN** the current-date tool is invoked against a fixed clock in a test
- **THEN** it returns exactly the ISO-8601 date and day of week corresponding to that clock's instant in the system time zone

## MODIFIED Requirements

### Requirement: Clarification on ambiguous instructions
The assistant's system prompt SHALL instruct it to ask a clarifying question in its reply, rather than guess, when the user's instruction is ambiguous or cannot be fulfilled with the available tools.

#### Scenario: Ambiguous instruction
- **WHEN** the user's instruction is ambiguous or outside what the available tools can do
- **THEN** the reply asks a clarifying question instead of guessing
