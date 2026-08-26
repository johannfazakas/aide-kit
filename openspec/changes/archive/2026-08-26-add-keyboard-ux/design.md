# Design — add-keyboard-ux

## Context

The Compose Multiplatform web client draws into a single canvas element, so the browser sees no text (native Ctrl+F is unfixable by construction) and unconsumed keys like Tab belong to the browser (focus would jump to the address bar). All visible text already lives in the screen models (`transcript`, `tasks`), which makes an in-app find cheap. The chat input is a `singleLine` `OutlinedTextField` with a guarded Send button; the create/edit task forms have guarded submit buttons; `App()` already exposes a `topBar` slot that only the Android build fills.

## Goals / Non-Goals

**Goals:**

- Keyboard-first flows on the web client: send, submit, field traversal, find.
- Keep commonMain framework-conventional and platform-free; web-only behavior enters through the platform seam like Android's top bar does.
- The in-app find must fully own Ctrl+F — the browser's native find bar must not appear.

**Non-Goals:**

- No find affordance on Android (no persistent keyboard; revisit if wanted via a top-bar icon later).
- No soft-keyboard newline path on Android (IME key is Send).
- No search over the task list beyond the existing filter field; no service or `client-core` API changes.

## Decisions

### Chat input: multiline with Enter-send

`ChatScreen.kt` drops `singleLine`; the field grows up to ~5 visible lines. Hardware keyboards: an `onPreviewKeyEvent` on the field sends on Enter (same guard as the Send button: non-blank, not sending) and lets Shift+Enter fall through as a newline. Android soft keyboard: `KeyboardOptions(imeAction = ImeAction.Send)` + `KeyboardActions(onSend = …)` — the IME action key can be Send or newline, not both; Send wins (accepted limitation, recorded in the proposal).

### Forms: Enter submits, Tab traverses

The create-task form and edit dialog submit on Enter from any field under the existing guards. Tab / Shift+Tab maps to `FocusManager.moveFocus(FocusDirection.Next / Previous)` and is consumed, so focus stays inside the canvas. *Alternative — rely on CMP's built-in Tab traversal*: behavior on wasm is version-dependent; the explicit mapping makes the requirement hold regardless (the spike below tells us whether it was already free).

### Find: web-only through the slot pattern

commonMain exposes only the hooks find needs: a highlight query threaded to `ChatScreen` (matches rendered as `AnnotatedString` background spans) and the chat `LazyListState` for `animateScrollToItem` cycling; a focus handle for the tasks filter field. `wasmJsMain` owns the rest: the find bar UI, Ctrl+F handling, match state. Match semantics: case-insensitive substring over the transcript, match count displayed, Enter/Shift+Enter cycles next/prev, Escape closes. On the tasks screen Ctrl+F focuses the existing filter field — the list already filters; a second search affordance would compete with it. Pure match-finding/cycling logic is extracted for common unit tests (given-when-then).

### One root key dispatcher

A single `onPreviewKeyEvent` at the web root dispatches Ctrl+F / Escape (and backstops Tab), instead of listeners scattered per screen. Field-local semantics (Enter-send, Shift+Enter) stay on their fields in commonMain.

### Browser key ownership (hard requirement + spike)

Requirement: pressing Ctrl+F in the web client opens the in-app bar and the browser's native bar does not appear. Expected mechanism: consuming the event in Compose prevents the default. **Spike first task of apply**: verify on the wasm build for Ctrl+F and Tab; if consumption does not `preventDefault`, add a JS `keydown` listener in `wasmJsMain` that does — the requirement is fixed, only the mechanism is free. Record the outcome here.

**Outcome**: resolved by construction instead of empirically — a document-level `keydown` listener in `wasmJsMain/Main.kt` calls `preventDefault()` for Ctrl/Cmd+F and Tab unconditionally, making suppression deterministic across CMP versions rather than dependent on whether Compose's event consumption reaches `preventDefault`. `preventDefault` does not stop propagation, so the canvas still receives the keys and the Compose-side handlers (find routing, `moveFocus`) work off the same events. Escape close and find routing also live in that listener; Enter/Shift+Enter semantics stay on their Compose fields.

## Risks / Trade-offs

- **CMP key-event behavior on wasm varies by version** — mitigated by the spike-first task and the JS-listener fallback.
- **Multiline input changes chat layout** (field height grows) — capped visible lines; verified in the live browser pass.
- **Highlight spans rebuild message text on query change** — transcript is bounded (session memory window), cost is negligible.
- **Android inherits the multiline field** — IME Send keeps behavior sensible; parity note captured in the `android-client` spec delta.
