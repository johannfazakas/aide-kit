# Spec: web-client

## Purpose

Provide a Compose Multiplatform web client for the aide-kit service: a task management screen and an assistant chat screen, served by a dedicated web server separate from the API, with screen state and logic living in the shared client-side module so future clients can reuse them.

## Requirements

### Requirement: Task management screen
The web client SHALL provide a task screen that lists the user's tasks and supports creating, editing (full-replace, including completion), and deleting tasks through the service's REST API. Deletion SHALL require an in-app confirmation before the request is sent. The screen SHALL provide a text filter that narrows the visible list client-side, and SHALL re-fetch the list when the screen is navigated to as well as on an explicit refresh action.

#### Scenario: Listing and filtering
- **WHEN** the user opens the task screen and types into the filter field
- **THEN** the current tasks are fetched from the service and the visible list narrows to tasks matching the filter text

#### Scenario: Creating a task
- **WHEN** the user submits the create form with a title (and optional due date and category)
- **THEN** the task is created via `POST /api/v1/tasks` and appears in the list

#### Scenario: Completing a task via edit
- **WHEN** the user marks a listed task as completed
- **THEN** the task is updated via the full-replace `PUT` with its other fields preserved and the list reflects the change

#### Scenario: Deleting with confirmation
- **WHEN** the user asks to delete a task and confirms the dialog
- **THEN** the task is deleted via `DELETE /api/v1/tasks/{id}` and leaves the list; cancelling the dialog sends no request

#### Scenario: Refresh after assistant changes
- **WHEN** tasks were changed through the chat assistant and the user navigates to the task screen or presses refresh
- **THEN** the list reflects the current server state

### Requirement: Chat screen
The web client SHALL provide a chat screen with a message input and a running transcript of the conversation. It SHALL carry the `sessionId` returned by the first exchange into subsequent requests, keep it only in memory (a page reload starts a fresh conversation), and display the assistant's replies as they arrive. Failures (including an unreachable service) SHALL surface as a visible error state rather than silently dropping messages.

#### Scenario: Continuous conversation
- **WHEN** the user sends several messages in a row
- **THEN** each request after the first carries the same `sessionId` and the transcript shows all exchanges in order

#### Scenario: Fresh conversation on reload
- **WHEN** the user reloads the page and sends a message
- **THEN** a new session is started with no memory of the previous transcript

#### Scenario: Failure surfaced
- **WHEN** a chat message fails (service unreachable or an error response)
- **THEN** the chat screen shows a visible error and the transcript keeps the user's message context

### Requirement: Split serving with CORS
The web client bundle SHALL be served by a dedicated web server, separate from the API service, which SHALL NOT serve any static content. The service SHALL grant CORS to configured origins: allowed origins come from an environment variable (values normalized for trailing slashes and case), defaulting to loopback origins — localhost, 127.0.0.1, [::1] — on any scheme and port when unset; origins outside the configured set SHALL NOT be granted CORS headers.

#### Scenario: Web app calls the API cross-origin
- **WHEN** the browser loads the web client from the web server's origin and it calls the service API
- **THEN** the service responds with CORS headers for that origin and the calls succeed

#### Scenario: Foreign origin refused
- **WHEN** a browser request arrives with an origin outside the configured allowance
- **THEN** the service does not grant CORS headers for it

#### Scenario: Service serves no static content
- **WHEN** a browser requests `/` from the service
- **THEN** no web page is served — the service exposes only the API

### Requirement: Shared presentation state
The task and chat screens' state and logic (loaded tasks, filter text, transcript, session id, error states, and the actions that mutate them) SHALL live in the client-side shared multiplatform module (`client-core`), consuming the API clients, so a future mobile client can reuse them without the web UI. The service SHALL NOT depend on this module.

#### Scenario: State exercised without UI
- **WHEN** the shared state holders are driven in tests against a mocked HTTP engine
- **THEN** loading, filtering, create/edit/delete, chat session continuity, and error transitions are observable without any Compose UI involved
