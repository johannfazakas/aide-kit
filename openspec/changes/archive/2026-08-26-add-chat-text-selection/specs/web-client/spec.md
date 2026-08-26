# web-client

## MODIFIED Requirements

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
