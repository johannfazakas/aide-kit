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
The web client SHALL provide a chat screen with a message input and a running transcript of the conversation. It SHALL carry the `sessionId` returned by the first exchange into subsequent requests, keep it only in memory (a page reload starts a fresh conversation), and display the assistant's replies as they arrive. Failures (including an unreachable service) SHALL surface as a visible error state rather than silently dropping messages. Assistant messages SHALL render common markdown notation — bold, italic, inline code, bullet lists, and headings — as styled text with the markers stripped; unsupported or malformed notation SHALL fall back to literal text. User messages SHALL render exactly as typed. Transcript text SHALL be selectable with the mouse and copyable to the system clipboard, yielding the rendered text as displayed.

#### Scenario: Continuous conversation
- **WHEN** the user sends several messages in a row
- **THEN** each request after the first carries the same `sessionId` and the transcript shows all exchanges in order

#### Scenario: Fresh conversation on reload
- **WHEN** the user reloads the page and sends a message
- **THEN** a new session is started with no memory of the previous transcript

#### Scenario: Failure surfaced
- **WHEN** a chat message fails (service unreachable or an error response)
- **THEN** the chat screen shows a visible error and the transcript keeps the user's message context

#### Scenario: Assistant markdown rendered
- **WHEN** the assistant replies with text containing `**bold**` words and `- ` bullet lines
- **THEN** the transcript shows the words bolded and the lines bulleted, with no literal `**` or `- ` markers visible

#### Scenario: Malformed markdown degrades to text
- **WHEN** an assistant reply contains an unclosed marker such as a lone `**`
- **THEN** the affected text renders literally and the rest of the message still renders styled

#### Scenario: Select and copy from the transcript
- **WHEN** the user drags a selection across transcript text (including across message boundaries) and presses Ctrl/Cmd+C
- **THEN** the selected rendered text lands on the system clipboard, without markdown markers

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

### Requirement: Keyboard send and submit
On hardware keyboards, the chat input SHALL send the message on Enter under the same conditions that enable the Send button, and SHALL insert a newline on Shift+Enter; the chat input SHALL accept multiline messages. The create-task form and the edit-task dialog SHALL submit on Enter from any of their fields under their existing validation guards. Tab and Shift+Tab SHALL move focus to the next and previous field, consumed by the application so focus does not leave the canvas to the browser chrome.

#### Scenario: Enter sends, Shift+Enter breaks the line
- **WHEN** the user types a chat message and presses Shift+Enter then more text then Enter
- **THEN** the message is sent as one multiline message, and pressing Enter with a blank input or while a send is in flight does nothing

#### Scenario: Enter submits the create-task form
- **WHEN** the user fills the new-task title and presses Enter in any of the form's fields
- **THEN** the task is created exactly as if the Add button were clicked, and Enter does nothing while the guard (blank title or invalid date) fails

#### Scenario: Tab cycles fields without escaping the app
- **WHEN** the user presses Tab (or Shift+Tab) in a form field
- **THEN** focus moves to the next (or previous) field and the browser chrome does not take focus

### Requirement: In-app find
The web client SHALL open an in-app find bar on Ctrl+F, and the browser's native find bar SHALL NOT appear. On the chat screen the bar SHALL search the rendered transcript text (markdown markers stripped) case-insensitively, show the match count, highlight matches, cycle to the next/previous match on Enter/Shift+Enter (scrolling the transcript to the match), and close on Escape. On the tasks screen Ctrl+F SHALL focus the existing filter field instead. The find affordance is web-only.

#### Scenario: Native find suppressed
- **WHEN** the user presses Ctrl+F anywhere in the web client
- **THEN** the in-app find affordance activates and the browser's native find bar does not open

#### Scenario: Cycling chat matches
- **WHEN** the user searches a term with several case-insensitive matches in the transcript and presses Enter repeatedly
- **THEN** the match count is shown and the transcript scrolls to each highlighted match in turn, wrapping after the last; Shift+Enter cycles backwards

#### Scenario: Find on the tasks screen
- **WHEN** the user presses Ctrl+F on the tasks screen
- **THEN** the existing filter field receives focus and no separate find bar opens

#### Scenario: Escape closes find
- **WHEN** the user presses Escape while the find bar is open
- **THEN** the bar closes and the transcript highlights are removed

#### Scenario: Find matches rendered text
- **WHEN** the assistant's reply shows a bolded word and the user searches that word
- **THEN** the word matches and is highlighted in place, and searching for the literal marker characters (`**`) yields no match

### Requirement: Visual theme
The web client SHALL use a deliberate visual theme shared with the Android app: a neutral palette (near-monochrome surfaces with a single accent color) defined as explicit light and dark Material color schemes, selected automatically from the browser's color-scheme preference; icon-based navigation (no emoji glyphs in app chrome); a defined typography scale; and consistent screen padding. On narrow viewports the create-task form SHALL lay out without truncating or wrapping its field labels.

#### Scenario: Dark scheme follows the browser
- **WHEN** the user's OS/browser prefers a dark color scheme and the app is loaded
- **THEN** the client renders with the dark color scheme, and with the light scheme when the preference is light

#### Scenario: Icons in navigation
- **WHEN** the user views the navigation bar
- **THEN** the Tasks and Chat destinations show Material icons with labels, not emoji characters

#### Scenario: Narrow-width form layout
- **WHEN** the tasks screen renders at a phone-width viewport
- **THEN** all create-task form labels are fully readable without wrapping mid-word, and the form remains usable

#### Scenario: Navigation adapts to window width
- **WHEN** the app renders in a window at least 840dp wide
- **THEN** the Tasks/Chat destinations appear as a vertical rail on the left edge, and as a bottom bar when the window is narrower
