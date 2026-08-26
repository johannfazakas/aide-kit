# Tasks — add-chat-text-selection

## 1. Selection

- [x] 1.1 Wrap the chat transcript `LazyColumn` in a single `SelectionContainer` in `ChatScreen` (commonMain); input row, error line, and find bar stay outside

## 2. Web clipboard verification

- [x] 2.1 On the wasm build, verify Ctrl/Cmd+C copies the Compose selection to the system clipboard; if not, add the JS bridge fallback (document-level Ctrl/Cmd+C → `navigator.clipboard.writeText` of the selection). Record the outcome in design.md

## 3. Verification

- [x] 3.1 `./gradlew build` green (all targets, all tests)
- [x] 3.2 Live check: web — drag-select across two messages, Ctrl/Cmd+C, paste elsewhere shows rendered text without markers; find highlights still render inside selectable messages. Android — long-press select, toolbar Copy, paste into the chat input
