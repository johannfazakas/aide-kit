# web-client

## MODIFIED Requirements

### Requirement: Chat screen
The web client SHALL provide a chat screen with a message input and a running transcript of the conversation. It SHALL carry the `sessionId` returned by the first exchange into subsequent requests, keep it only in memory (a page reload starts a fresh conversation), and display the assistant's replies as they arrive. Failures (including an unreachable service) SHALL surface as a visible error state rather than silently dropping messages. Assistant messages SHALL render common markdown notation — bold, italic, inline code, bullet lists, and headings — as styled text with the markers stripped; unsupported or malformed notation SHALL fall back to literal text. User messages SHALL render exactly as typed.

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
