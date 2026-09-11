## Why

A high-effort review of the add-task-edits change confirmed eight correctness bugs in the new mutation path, three of which share a single root cause: the full-replace repository contract forces a non-atomic read-merge-write, so an intent edit can silently revert concurrent vault hand-edits, can never clear an unparseable `[due::]` value, and costs two to three git pulls per operation. The remaining bugs corrupt or churn vault content (newline titles split blocks, CRLF files are rewritten wholesale, topic names with `$` crash the write, inline Dataview fields get duplicated, note lines are orphaned on topic moves).

## What Changes

- **BREAKING (internal contract)**: `TaskRepository.update(id, title, dueDate, topic, done)` becomes a patch/change-set contract — each field is either absent (keep) or set (possibly to null). The Obsidian backend merges the patch against freshly pulled vault state inside a single serialized write (one pull per mutation); the service no longer pre-reads and merges. `PUT` maps to a patch with all fields set; intent operations name only their field, which also makes "clear an unparseable due value" expressible. `changeTopic` validates against the same scan (no extra pull).
- **Vault revision token (optimistic concurrency)**: task read/list responses carry the vault revision; mutation requests echo it and fail with `409` when the vault has advanced since; mutation responses return the new revision. In-memory backend uses a counter. Assistant tools carry the revision and re-list on the conflict error.
- **Inline field support (read liberally, write canonically)**: the scanner also parses `[key:: value]` fields on the checkbox line itself; edits migrate a *changed* inline field to its own follow-up line while untouched inline fields stay byte-identical.
- **Correctness fixes**: titles containing line breaks are rejected (`400`); topic values are escaped before regex replacement; task blocks include consecutive indented free-text lines (notes survive topic relocation, fields after a note line are found); file line terminators (CRLF) are preserved on rewrite; scanner and repository share one canonical checkbox/field grammar and a checkbox line that cannot be edited fails loudly instead of reporting phantom success.
- **Documented limitation**: byte-identical unstamped twin tasks may collide or lose addressability after one is edited (stamping all twins was considered and rejected for simplicity; a re-list recovers).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `task-management`: mutation endpoints gain the revision token contract (`409` on stale revision, new revision in responses); title validation tightens to single-line; update semantics restated as patch-on-current-state at every layer.
- `obsidian-task-storage`: update becomes a single-pull patch merge against fresh state; surgical-rewrite requirements extend to inline checkbox-line fields (normalize-on-edit), free-text block lines, and line-terminator preservation; unparseable due values become clearable; twin-collision limitation documented.
- `assistant-chat`: edit tools become revision-aware — they pass the revision from the last listing and re-list-and-retry when the vault has changed.

## Impact

- `service/`: `TaskRepository` + both implementations, `TaskService` (drop read-merge, keep validation), `VaultScanner` (inline fields, block bounds, shared grammar), `ObsidianTaskRepository` (patch merge, escaping, line endings, loud failures), `VaultGitBridge` (expose revision), `TaskRoutes` + `StatusPages` (revision echo, `409`), `agent/TaskTools` + system prompt (revision handling).
- `shared/`: mutation request/response transfer models gain revision fields.
- Derived ids of unstamped inline-field tasks re-derive once at deploy (parser now sees clean title + due); nothing persists ids across a restart, so this is unobservable — noted in the spec.
- Supersedes the "full-replace contract, intent merge in the service" decision recorded in the archived `2026-09-04-add-task-edits` design.
- Tests: contract tests for patch semantics on both backends, revision-conflict paths, inline-field and block-bounds scenarios, CRLF round-trip; git behavior stays on local temp repos; nothing calls an LLM.
