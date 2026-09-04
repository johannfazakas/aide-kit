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
A web chat interface SHALL be available (the Compose web client's chat screen, served by the web deployment) that lets a user hold a conversation with the assistant from a browser: an input for messages, a running display of the exchange, and calls to `POST /api/v1/chat`. The interface SHALL carry the `sessionId` returned by the first exchange into subsequent requests so the conversation is continuous.

#### Scenario: Chatting from the page
- **WHEN** the user opens the web client's chat screen, types a message, and submits it
- **THEN** the message and the assistant's reply appear in the running display, and the next message continues the same session

#### Scenario: New conversation
- **WHEN** the user reloads the page
- **THEN** a fresh conversation begins with no prior history

### Requirement: Task management through the assistant
The assistant SHALL be able to list tasks (optionally filtered by topic), list the known topics, retrieve a task by id, create tasks, and update tasks (including marking them done) by invoking tools backed by the existing task service. A created task's topic must be one of the known topics or absent; when the user names a topic outside the list, the assistant SHALL consult the topics tool and clarify with the user (suggesting close matches or offering to capture without a topic) rather than guessing or inventing a topic. The assistant SHALL NOT be able to delete tasks. When the active storage backend does not support an operation (updates in Obsidian mode), the tool SHALL return the backend's not-supported error to the assistant, and the reply SHALL relay that the operation is not available yet.

#### Scenario: Creating a task via natural language
- **WHEN** the user asks the assistant to add a task with a given title
- **THEN** the task is created through the task service and the reply confirms the created task

#### Scenario: Listing tasks via natural language
- **WHEN** the user asks the assistant what tasks exist (optionally for a topic)
- **THEN** the assistant invokes the list tool and the reply reflects the tasks currently in the store

#### Scenario: Completing a task via natural language
- **WHEN** the user asks the assistant to mark an identified task as done
- **THEN** the task is updated with `done = true` through the task service and the reply confirms it

#### Scenario: No deletion capability
- **WHEN** the user asks the assistant to delete a task
- **THEN** no task is deleted and the reply explains the assistant cannot delete tasks

#### Scenario: Unknown topic clarified instead of guessed
- **WHEN** the user asks for a task under a topic that is not in the known-topics list
- **THEN** no task is created with that topic and the reply asks the user to pick a known topic (or none), naming close matches when they exist

#### Scenario: Capture without a topic
- **WHEN** the user asks the assistant to add a task and no topic is given or agreed
- **THEN** the task is created without a topic and the reply confirms it landed in the inbox for later grooming

#### Scenario: Update not supported by the backend
- **WHEN** Obsidian storage is active and the user asks the assistant to mark a task as done
- **THEN** the update tool returns the not-supported error, no vault content changes, and the reply explains that completing tasks is not available yet

### Requirement: Multi-step tool execution
The assistant SHALL complete multi-step tool flows within a single chat request: when carrying out an instruction requires several tool invocations in sequence (such as looking a task up and then updating it), it SHALL keep invoking tools after receiving tool results until the instruction is carried out. Narration text accompanying a tool call SHALL NOT end the agent run before that tool call is executed, and the assistant SHALL NOT reply that it is about to perform an action without performing it in the same request.

#### Scenario: Completing a task found by lookup
- **WHEN** the user asks the assistant to mark a task as done, identifying it by content or by reference to an earlier turn, so the assistant must first look up the task id
- **THEN** within that same request the assistant invokes the update tool after the lookup, the task's `done` becomes `true`, and the reply confirms the completed task

#### Scenario: Narration alongside a tool call
- **WHEN** the model's response after a tool result contains both narration text and a further tool call
- **THEN** the tool call is executed and the run continues, rather than the narration ending the run

#### Scenario: Plain answers still allowed
- **WHEN** the user's message needs no tool action, or the tools' results already answer it
- **THEN** the assistant replies in plain text and the run ends normally, without being forced into further tool calls

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

### Requirement: Clarification on ambiguous instructions
The assistant's system prompt SHALL instruct it to ask a clarifying question in its reply, rather than guess, when the user's instruction is ambiguous or cannot be fulfilled with the available tools.

#### Scenario: Ambiguous instruction
- **WHEN** the user's instruction is ambiguous or outside what the available tools can do
- **THEN** the reply asks a clarifying question instead of guessing

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

### Requirement: Startup requires LLM configuration
The service SHALL refuse to start when the LLM provider API key (`OPENCODE_API_KEY`) is not configured, terminating with an error message that names the missing variable. No degraded or partial mode SHALL exist.

#### Scenario: Missing key aborts startup
- **WHEN** the service starts without `OPENCODE_API_KEY`
- **THEN** it exits with an error naming the variable and serves no requests
