# Spec: assistant-chat

## Purpose

Provide a natural-language assistant over the task API: a stateless chat endpoint backed by an LLM agent that can list, create, and update tasks through tools, with clear error behavior when the LLM is unavailable or misconfigured.

## Requirements

### Requirement: Chat endpoint
The system SHALL expose `POST /api/v1/chat` accepting a JSON body `{"message": "<text>"}` and responding with a JSON body `{"reply": "<text>"}` containing the assistant's answer.

#### Scenario: Successful chat exchange
- **WHEN** a client posts `{"message": "..."}` with a non-blank message and the LLM provider is configured
- **THEN** the system responds `200 OK` with `{"reply": "..."}` containing the assistant's natural-language answer

#### Scenario: Blank or missing message
- **WHEN** a client posts a body with a missing or blank `message`
- **THEN** the system responds `400 Bad Request` with an error body explaining the message must not be blank

### Requirement: Stateless conversations
Each chat request SHALL be processed as an independent conversation. The system SHALL NOT retain conversation history between requests.

#### Scenario: No memory across requests
- **WHEN** a client sends a message referring to a previous exchange (e.g., "mark it as done") in a new request
- **THEN** the assistant answers without access to prior exchanges, asking for the missing details if needed

### Requirement: Task management through the assistant
The assistant SHALL be able to list tasks (optionally filtered by category), retrieve a task by id, create tasks, and update tasks (including marking them completed) by invoking tools backed by the existing task service. The assistant SHALL NOT be able to delete tasks.

#### Scenario: Creating a task via natural language
- **WHEN** the user asks the assistant to add a task with a given title
- **THEN** the task is created through the task service and the reply confirms the created task

#### Scenario: Listing tasks via natural language
- **WHEN** the user asks the assistant what tasks exist (optionally for a category)
- **THEN** the assistant invokes the list tool and the reply reflects the tasks currently in the store

#### Scenario: Completing a task via natural language
- **WHEN** the user asks the assistant to mark an identified task as done
- **THEN** the task is updated with `completed = true` through the task service and the reply confirms it

#### Scenario: No deletion capability
- **WHEN** the user asks the assistant to delete a task
- **THEN** no task is deleted and the reply explains the assistant cannot delete tasks

### Requirement: Clarification on ambiguous instructions
The assistant's system prompt SHALL instruct it to ask a clarifying question in its reply, rather than guess, when the user's instruction is ambiguous or cannot be fulfilled with the available tools — including relative dates, which the assistant cannot resolve without current-date awareness.

#### Scenario: Relative due date
- **WHEN** the user asks for a task due "tomorrow" or another relative date
- **THEN** the reply asks for the concrete date instead of inventing one

### Requirement: Tool error recovery
Tool invocations that fail due to domain errors (unknown task id, invalid input) SHALL return the error description to the assistant as a tool result, allowing it to react (e.g., re-list tasks or ask the user), rather than aborting the chat request.

#### Scenario: Unknown task id used by the assistant
- **WHEN** a tool call references a task id that does not exist
- **THEN** the tool returns a not-found error message to the assistant and the request still completes with a helpful reply

### Requirement: LLM failure surfacing
When the agent run fails because of the LLM provider (rejected key, unavailable gateway, model error), the chat endpoint SHALL respond `502 Bad Gateway` with an error body containing the underlying failure reason, rather than an empty `500`.

#### Scenario: Gateway rejects the request
- **WHEN** a chat message is posted and the LLM gateway rejects or fails the request
- **THEN** the system responds `502 Bad Gateway` with an error body describing the cause

### Requirement: Degraded mode without LLM configuration
When the LLM provider API key (`OPENCODE_API_KEY`) is not configured, the application SHALL still start and serve the task REST API, and the chat endpoint SHALL respond `503 Service Unavailable` with an error body explaining the assistant is not configured.

#### Scenario: Chat without API key
- **WHEN** the application runs without `OPENCODE_API_KEY` and a client posts a chat message
- **THEN** the system responds `503 Service Unavailable` with an error body explaining the assistant is not configured

#### Scenario: Task API unaffected by missing key
- **WHEN** the application runs without `OPENCODE_API_KEY`
- **THEN** all `/api/v1/tasks` endpoints behave exactly as specified in task-management
