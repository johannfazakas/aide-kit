# Tasks — add-keyboard-ux

## 1. Key-ownership spike (web)

- [x] 1.1 On the wasm build, verify whether consuming Ctrl+F and Tab in Compose prevents the browser's default handling; if not, add a JS `keydown` listener in `wasmJsMain` that does. Record the outcome in design.md

## 2. Send and submit semantics

- [x] 2.1 Chat input: drop `singleLine` (cap ~5 visible lines); Enter sends / Shift+Enter inserts a newline via `onPreviewKeyEvent` under the Send button's guard; Android gets `KeyboardOptions(imeAction = ImeAction.Send)` + `KeyboardActions(onSend)`
- [x] 2.2 Create-task form and edit dialog submit on Enter from any field under the existing guards
- [x] 2.3 Tab / Shift+Tab traversal via `FocusManager.moveFocus(Next/Previous)`, consumed so focus stays in the canvas

## 3. In-app find (web-only)

- [x] 3.1 commonMain hooks: highlight query rendered as `AnnotatedString` spans in chat messages; chat `LazyListState` and tasks-filter focus handle exposed through the platform seam (slot pattern, like the Android top bar)
- [x] 3.2 `wasmJsMain`: root key dispatcher (Ctrl+F / Escape) and the find bar — case-insensitive matching, match count, Enter/Shift+Enter cycling with scroll-to-match and wrap-around, Escape closes and clears highlights
- [x] 3.3 Extract pure match-finding/cycling logic and cover it with common unit tests (given-when-then); build stays LLM- and Docker-free

## 4. Docs & verification

- [x] 4.1 README: short keyboard-shortcuts note in the web client section (Enter/Shift+Enter, Tab, Ctrl+F)
- [x] 4.2 `./gradlew build` green (wasm + android targets, all tests)
- [x] 4.3 Live browser check: multiline Enter/Shift+Enter send, Enter form submit, Tab/Shift+Tab traversal, Ctrl+F end-to-end incl. native-bar suppression and tasks-screen filter focus; sanity-check the Android chat input still sends via the IME action key
