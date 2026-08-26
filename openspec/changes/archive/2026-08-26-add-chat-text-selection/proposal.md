# Proposal: add-chat-text-selection

## Why

Chat transcript text cannot be selected or copied on either client — Compose `Text` is non-selectable by default, and the web canvas offers no browser-native selection to fall back on. The assistant's replies are exactly the kind of content users want to copy out (task details, generated text), so the transcript should behave like normal text.

## What Changes

- The chat transcript becomes selectable on both clients: mouse drag (web) or long-press (Android) selects text across the conversation, and the selection can be copied to the system clipboard — Ctrl/Cmd+C on the web, the selection toolbar's Copy on Android.
- Selection composes with the existing rendering: what gets copied is the rendered text (markdown markers stripped), and find highlights keep working inside selectable messages.
- Scope is the chat transcript; task-screen text stays as-is (its content is editable through forms already).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `web-client`: the chat screen requirement gains selectable/copyable transcript text.
- `android-client`: parity — long-press selection with the standard toolbar copy on the same shared screen.

## Impact

- `client` commonMain: `ChatScreen` wraps the transcript in a `SelectionContainer`; no model or `client-core` changes.
- Web-only risk to verify during apply: clipboard write from the wasm canvas (Compose's clipboard integration vs the browser's async clipboard API) — fallback recorded in the design if Ctrl/Cmd+C doesn't reach the system clipboard.
- No service changes.
