# Spec: assistant-chat

## Purpose

Provide a natural-language assistant over the task API: a chat endpoint with session-scoped conversation memory, backed by an LLM agent that can list, create, and update tasks through tools, with clear error behavior when the LLM is unavailable or misconfigured.

## Requirements

### Requirement: Chat endpoint
The system SHALL expose `POST /api/v1/chat` accepting a JSON body `{"sessionId": "<id>"?, "message": "<text>"}` where `sessionId` is optional, and responding with a JSON body `{"sessionId": "<id>", "reply": "<text>"}`. When the request omits `sessionId`, the system SHALL mint a new session id and return it. When the request includes a known `sessionId`, the system SHALL continue that conversation. When the request includes an unknown `sessionId`, the system SHALL NOT adopt it — it SHALL mint a fresh session id and return it. The returned `sessionId` identifies the conversation for follow-up requests.

#### Scenario: Successful chat exchange
- **WHEN** a client posts `{"message": "..."}` with a non-blank message and the LLM provider is configured
- **THEN** the system responds `200 OK` with `{"sessionId": "...", "reply": "..."}` containing a session id and the assistant's natural-language answer

#### Scenario: Continuing an existing session
- **WHEN** a client posts a message with a `sessionId` returned by a prior exchange
- **THEN** the assistant's reply reflects awareness of the earlier turns in that session

#### Scenario: Starting a fresh conversation
- **WHEN** a client posts a message without a `sessionId`
- **THEN** the system creates a new conversation, returns its `sessionId`, and the reply does not reflect any prior conversation

#### Scenario: Unknown session id
- **WHEN** a client posts a message with a `sessionId` the server did not mint (or one lost to a restart)
- **THEN** the system starts a fresh conversation under a newly minted `sessionId` and returns that id, rather than adopting the client-supplied one

#### Scenario: Blank or missing message
- **WHEN** a client posts a body with a missing or blank `message`
- **THEN** the system responds `400 Bad Request` with an error body explaining the message must not be blank

### Requirement: Session-scoped conversation memory
The system SHALL retain conversation history in memory per session and include prior turns of the same session when the assistant processes a new message, so the assistant can resolve references to earlier turns. History is not persisted across application restarts. Distinct session ids SHALL NOT share history.

#### Scenario: Memory within a session
- **WHEN** a client creates a task in one message and, in a later message of the same session, refers to it (e.g., "mark it as done") without repeating its id
- **THEN** the assistant resolves the reference from session history and acts on the correct task

#### Scenario: Isolation between sessions
- **WHEN** two requests use different session ids
- **THEN** neither reply reflects the other session's history

#### Scenario: History lost on restart
- **WHEN** the application restarts and a client reuses a session id from before the restart
- **THEN** the assistant has no memory of the pre-restart turns

### Requirement: Bounded conversation history
Each session's retained history SHALL be bounded to a recent window of turns, so a long conversation does not grow memory or per-request token usage without limit. When the window is exceeded, the oldest turns SHALL be dropped first.

#### Scenario: Long conversation stays bounded
- **WHEN** a session accumulates more turns than the retained window
- **THEN** only the most recent turns within the window are sent to the assistant on the next message

### Requirement: Chat web UI
The application SHALL serve a minimal static chat page that lets a user hold a conversation with the assistant from a browser: an input for messages, a running display of the exchange, and calls to `POST /api/v1/chat`. The page SHALL carry the `sessionId` returned by the first exchange into subsequent requests so the conversation is continuous.

#### Scenario: Chatting from the page
- **WHEN** the user opens the chat page, types a message, and submits it
- **THEN** the message and the assistant's reply appear in the running display, and the next message continues the same session

#### Scenario: New conversation
- **WHEN** the user reloads the chat page
- **THEN** a fresh conversation begins with no prior history

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
