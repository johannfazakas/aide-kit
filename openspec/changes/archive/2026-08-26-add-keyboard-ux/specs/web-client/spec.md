# web-client

## ADDED Requirements

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
The web client SHALL open an in-app find bar on Ctrl+F, and the browser's native find bar SHALL NOT appear. On the chat screen the bar SHALL search the transcript case-insensitively, show the match count, highlight matches, cycle to the next/previous match on Enter/Shift+Enter (scrolling the transcript to the match), and close on Escape. On the tasks screen Ctrl+F SHALL focus the existing filter field instead. The find affordance is web-only.

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
