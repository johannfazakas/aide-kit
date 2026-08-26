# android-client

## MODIFIED Requirements

### Requirement: Android app on shared screens
The Android application SHALL render the same task management and chat screens as the web client, built from the common Compose UI and the shared screen models, against the service's REST and chat API. Feature behavior (task CRUD with delete confirmation and date validation, list refresh semantics, chat session continuity, error surfacing, assistant markdown rendering, selectable transcript text) SHALL match the web client's requirements, except web-only keyboard affordances (in-app find, Enter/Shift+Enter and Tab semantics), which the Android app does not provide. The chat input SHALL accept multiline messages, and the soft keyboard's action key SHALL send the message; there is no soft-keyboard newline path.

#### Scenario: Task and chat parity
- **WHEN** the user operates the task screen and holds a multi-turn chat conversation on the Android app
- **THEN** behavior matches the web client against the same service, including tool-driven task changes appearing after a list refresh

#### Scenario: Soft keyboard sends
- **WHEN** the user types a chat message and taps the soft keyboard's action key
- **THEN** the message is sent under the same conditions that enable the Send button

#### Scenario: Assistant markdown rendered on Android
- **WHEN** the assistant replies with bold words and bullet lists on the Android app
- **THEN** the transcript renders them styled, identically to the web client

#### Scenario: Long-press select and copy
- **WHEN** the user long-presses a word in the transcript, adjusts the selection handles, and taps Copy in the selection toolbar
- **THEN** the selected rendered text lands on the device clipboard
