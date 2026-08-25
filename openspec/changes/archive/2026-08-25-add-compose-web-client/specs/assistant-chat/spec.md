# assistant-chat

## MODIFIED Requirements

### Requirement: Chat web UI
A web chat interface SHALL be available (the Compose web client's chat screen, served by the web deployment) that lets a user hold a conversation with the assistant from a browser: an input for messages, a running display of the exchange, and calls to `POST /api/v1/chat`. The interface SHALL carry the `sessionId` returned by the first exchange into subsequent requests so the conversation is continuous.

#### Scenario: Chatting from the page
- **WHEN** the user opens the web client's chat screen, types a message, and submits it
- **THEN** the message and the assistant's reply appear in the running display, and the next message continues the same session

#### Scenario: New conversation
- **WHEN** the user reloads the page
- **THEN** a fresh conversation begins with no prior history

## ADDED Requirements

### Requirement: Startup requires LLM configuration
The service SHALL refuse to start when the LLM provider API key (`OPENCODE_API_KEY`) is not configured, terminating with an error message that names the missing variable. No degraded or partial mode SHALL exist.

#### Scenario: Missing key aborts startup
- **WHEN** the service starts without `OPENCODE_API_KEY`
- **THEN** it exits with an error naming the variable and serves no requests

## REMOVED Requirements

### Requirement: Degraded mode without LLM configuration
**Reason**: Replaced by fail-fast startup validation — with containerized deployment, a half-working service hides misconfiguration; a refused start is immediately visible.
**Migration**: Provide `OPENCODE_API_KEY` (e.g. via the root `.env` consumed by compose); there is no keyless mode.
