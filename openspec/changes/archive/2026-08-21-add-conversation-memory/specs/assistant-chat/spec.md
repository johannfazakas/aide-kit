# assistant-chat

## MODIFIED Requirements

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

## ADDED Requirements

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

## REMOVED Requirements

### Requirement: Stateless conversations
**Reason**: Replaced by session-scoped conversation memory — the assistant now retains per-session history so users can hold multi-turn conversations.
**Migration**: Clients that relied on each request being independent can start a fresh conversation by omitting `sessionId`; each omission mints a new isolated session.
