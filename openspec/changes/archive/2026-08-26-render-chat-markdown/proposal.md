# Proposal: render-chat-markdown

## Why

The assistant's replies come back as markdown — the model routinely bolds key words (`**Creating**`), writes bullet lists, and uses inline code — but both clients render message content as raw text, so users see literal asterisks and dashes instead of formatting. Seen live on both the web client and the Android emulator.

## What Changes

- Assistant messages in the chat transcript render common markdown notation as styled text: **bold**, *italic*, `inline code`, bullet lists, and headings. User messages stay plain (they are the user's own text).
- Rendering happens in the shared Compose screens, so the web and Android clients get it identically.
- The web client's find feature keeps working over what the user *sees*: matching and highlighting operate on the rendered text (markers stripped), not the raw markdown source, so a search for "Creating" matches and highlights the bolded word.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `web-client`: the chat screen requirement gains assistant-markdown rendering; the in-app find requirement is clarified to match over rendered text.
- `android-client`: inherits the same rendering through the shared screens (parity requirement already covers behavior matching; a scenario makes the markdown expectation explicit).

## Impact

- `client-core`: a small pure markdown-to-styled-text model (parse once, expose plain text + style spans) alongside the existing `ChatFind` logic, unit-tested; find logic composes with it by operating on the plain rendered text.
- `client` commonMain: `ChatScreen` renders assistant messages through the new model, merging find-highlight spans with markdown style spans.
- No service, transfer-model, or platform-specific changes; no new third-party dependency (a minimal hand-rolled renderer — see design for the trade-off against a markdown library).
