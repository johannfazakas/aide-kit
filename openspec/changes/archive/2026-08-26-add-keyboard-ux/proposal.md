# Proposal: add-keyboard-ux

## Why

The web client works but fights keyboard-first use: sending a chat message or adding a task requires clicking a button, moving between form fields requires the mouse, and Ctrl+F — the reflex for finding text in a page — does nothing useful because Compose Multiplatform renders the whole app onto a canvas, leaving the browser's native find with no text to search. These are the frictions felt most in daily use of the web client (explore session 2026-08-26).

## What Changes

- **Enter sends, Shift+Enter breaks the line.** The chat input becomes multiline-capable (it is `singleLine` today); on hardware keyboards Enter submits under the same guard as the Send button and Shift+Enter inserts a newline. The Android soft keyboard gets an IME Send action key instead (an IME key can't be both Send and newline — accepted limitation, no soft-newline path on Android for now).
- **Enter submits task forms.** The create-task form and the edit-task dialog submit on Enter under their existing validation guards.
- **Tab / Shift+Tab moves focus between fields**, consumed by the app so focus never escapes the canvas to the browser chrome.
- **In-app find on the web client.** Ctrl+F opens an in-app find bar (and suppresses the browser's native one — hard requirement): case-insensitive search over the chat transcript with a match count, Enter/Shift+Enter cycling that scrolls to each match, highlighted matches, Escape to close. On the tasks screen Ctrl+F focuses the existing filter field instead. Web-only: the find bar is wired in `wasmJsMain` through the same slot pattern the Android top bar uses; the Android app does not ship it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `web-client`: gains keyboard interaction requirements — Enter/Shift+Enter send semantics, Enter form submission, Tab traversal, and the in-app find behavior including native-find suppression.
- `android-client`: the shared chat screen's parity note — the chat input becomes multiline with an IME Send action; behavior otherwise unchanged.

## Impact

- `client/src/commonMain` screens (`ChatScreen.kt`, `TasksScreen.kt`, `App.kt`): key handling, multiline input, focus traversal, find hooks (highlight query + scroll seam).
- `client/src/wasmJsMain` (`Main.kt` + new find bar): Ctrl+F ownership, find UI, root key dispatcher.
- `client/src/androidMain`: none beyond inheriting the multiline field with `ImeAction.Send`.
- No service, `shared`, or `client-core` API changes expected; find logic that is pure (match finding/cycling) gets common unit tests.
