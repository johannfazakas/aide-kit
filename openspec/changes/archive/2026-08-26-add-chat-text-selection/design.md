# Design — add-chat-text-selection

## Context

`ChatScreen` renders transcript messages as `Text` composables (non-selectable by default) inside a `LazyColumn`, styled via `AnnotatedString` (markdown spans + find highlights). Compose's `SelectionContainer` is the standard way to make text selectable; it is multiplatform and drives the platform-native affordances — drag selection with keyboard copy on pointer platforms, long-press handles with the selection toolbar on Android.

## Goals / Non-Goals

**Goals:**

- Select and copy transcript text on both clients through platform-native gestures.
- Copied text is the rendered text (markers stripped) — what you see is what you copy.
- Keep the existing find-highlighting and markdown styling untouched.

**Non-Goals:**

- Task-screen selection (content is form-editable there; revisit on demand).
- A per-message "copy whole message" button — only add if selection-based copy proves impractical.
- Selection state surviving rotation (Android) or scroll-recycling of far-off items — platform-standard limitations are accepted.

## Decisions

### One `SelectionContainer` around the transcript

Wrap the `LazyColumn` in `ChatScreen` (commonMain) in a single `SelectionContainer`, so a drag can span multiple messages, rather than one container per message. Input row, error line, and the find bar stay outside the container. `LazyColumn` caveat: items disposed by recycling drop out of an active selection — accepted (chat messages are short; the transcript window is bounded).

### Clipboard on the web: verify, with a recorded fallback

On Android, copy comes from the standard selection toolbar — no risk. On wasm, `SelectionContainer` handles Ctrl/Cmd+C via Compose's clipboard integration, which must write through the browser's async clipboard API. **Verify during apply** on the wasm build; if the shortcut does not reach the system clipboard, the fallback is a wasm-side bridge: intercept Ctrl/Cmd+C in the existing document-level key listener, read the Compose selection, and write it via `navigator.clipboard.writeText`. The observable requirement (selection + copy works) is fixed; only the mechanism is free — record the outcome here, mirroring the Ctrl+F spike from add-keyboard-ux.

**Outcome**: no fallback needed — CMP's built-in clipboard integration works on wasm. Verified live in the browser (drag-select + Cmd+C, pasted outside the app as rendered text) and on the Android emulator (long-press → toolbar Copy → paste into the chat input, adb-driven).

### Interplay with existing key handling

Ctrl/Cmd+C is not intercepted by the app's key dispatcher (which only owns Ctrl+F, Tab, Escape), so nothing in our layer swallows the copy shortcut. The Escape handler closes the find bar; that behavior takes precedence over clearing a selection — acceptable.

## Risks / Trade-offs

- **CMP wasm clipboard maturity** — mitigated by the verify-first task and the JS-bridge fallback.
- **Selection vs LazyColumn recycling** — accepted platform limitation (see decisions).
- **Selection handles interacting with the find-highlight spans** — purely visual layering of background spans and selection color; verified in the live check.
