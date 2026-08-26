# Design — render-chat-markdown

## Context

Assistant replies are markdown-flavored plain text rendered verbatim in `ChatScreen` message bubbles. The chat screen already renders messages as `AnnotatedString` (the find feature adds highlight background spans via `matchRanges` from `client-core`). Any markdown solution must compose with that: find matches character offsets, so whatever text the user sees is the text find must search.

## Goals / Non-Goals

**Goals:**

- Render the markdown the model actually emits: bold, italic, inline code, bullet lists, headings.
- Identical rendering on web and Android via the shared screens.
- Find (web) matches and highlights over the rendered text, not raw markdown source.
- Pure, unit-testable parsing in `client-core`, consistent with the `ChatFind` pattern.

**Non-Goals:**

- Full CommonMark fidelity: no tables, images, block quotes, nested lists, or clickable links (the agent has no use for them today; revisit if the model's output changes).
- No markdown editing/preview for user input — user messages stay plain.

## Decisions

### Hand-rolled minimal renderer over a markdown library

A small parser in `client-core` (`presentation/ChatMarkdown.kt`) converts markdown source to a `RenderedMessage`: the plain display text (markers stripped) plus a list of style spans (`BOLD`, `ITALIC`, `CODE`, `HEADING`) with offsets into that display text. Line-level handling: `- ` / `* ` bullets become `• `, `#`-headings become styled lines. `ChatScreen` maps spans to `SpanStyle`s and merges find-highlight spans on top.

*Alternative — `mikepenz/multiplatform-markdown-renderer`*: full-fidelity CMP markdown as composables. Rejected for now on two grounds: (1) it renders its own composable tree, so the find feature's highlight spans and character offsets cannot reach inside it — find would either match raw markdown (wrong offsets, matches on `**`) or stop highlighting; (2) a dependency for what is today bold/bullets. Revisit if fidelity needs outgrow the minimal set — the `RenderedMessage` seam localizes the swap to `ChatScreen`.

### Find operates on rendered text

`ChatFind`'s matching moves from raw `message.content` to the rendered plain text of each message (user messages render as themselves). The find bar and `ChatScreen` must derive the text from the same pure `rendered()` function so match offsets align with displayed characters — each consumer memoizes its own call (per message in `ChatScreen`, per query+transcript in the find bar); determinism of the shared function is what rules out divergence, and the bounded transcript keeps the duplicate parse negligible.

### Only assistant messages parse as markdown

User bubbles show exactly what was typed. This also sidesteps surprising the user by reformatting their own message.

## Risks / Trade-offs

- **Parser edge cases** (unclosed `**`, nested emphasis): treat unmatched markers as literal text — degrade to today's behavior, never worse. Unit tests pin these.
- **Model output drifting beyond the minimal set** (tables, links): renders as literal text, same as today; the library alternative is the recorded escape hatch.
- **Double-parse divergence** between find and display: prevented structurally by sharing one rendered value (see decision above).
