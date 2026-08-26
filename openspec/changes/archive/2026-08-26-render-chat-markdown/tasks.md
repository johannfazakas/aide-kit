# Tasks — render-chat-markdown

## 1. Rendering model (client-core)

- [x] 1.1 `presentation/ChatMarkdown.kt`: parse markdown source to a `RenderedMessage` (plain display text + style spans with offsets) covering bold, italic, inline code, `- `/`* ` bullets (as `• `), and `#` headings; unmatched markers stay literal
- [x] 1.2 Unit tests (given-when-then) pinning the supported notation, marker stripping, offset correctness, and malformed-markdown fallbacks

## 2. Screen integration

- [x] 2.1 `ChatScreen` renders assistant messages from `RenderedMessage` (user messages plain), mapping style spans to `SpanStyle`s and merging find-highlight spans on top; render once per message and reuse
- [x] 2.2 Find pipeline (`ChatFind` callers: `ChatScreen` highlights + wasm `FindBar` matching) operates on the same rendered text so match offsets align with what is displayed

## 3. Verification

- [x] 3.1 `./gradlew build` green (all targets, all tests)
- [x] 3.2 Live check: chat on the web client shows bolded/bulleted assistant replies without literal markers; Ctrl+F matches and highlights inside a bolded word; Android sanity check shows the same rendering
