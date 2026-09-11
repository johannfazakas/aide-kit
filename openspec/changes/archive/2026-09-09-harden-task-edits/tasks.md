## 1. Shared grammar and scanner hardening

- [x] 1.1 Extract the checkbox and inline-field regexes (and the bold-title serialization) into one shared definition consumed by both `VaultScanner` and `ObsidianTaskRepository`; delete the repository's private copies
- [x] 1.2 Widen `parseTasks` block bounds to the checkbox line plus all consecutive indented non-blank lines (field or free text), parsing fields from any of them; keep `startLine`/`endLine` consistent with the new bounds
- [x] 1.3 Parse inline fields from the checkbox line's remainder, stripping them from the title before emphasis stripping; first occurrence of a key wins (checkbox line first, then follow-up lines)
- [x] 1.4 Unit-test the scanner changes (given-when-then): inline checkbox-line due/topic parsed, note line inside a block, field below a note line, duplicate key precedence, title clean of field expressions

## 2. Patch repository contract

- [x] 2.1 Introduce `TaskPatch` (per-field absent-vs-set semantics, carrying the intent for commit messages) and change `TaskRepository.update` to `update(id, patch, expectedRevision?)`; adapt `InMemoryTaskRepository` (merge inside `computeIfPresent`, counter-based revision)
- [x] 2.2 Rework `TaskService`: intent methods build single-field patches, `PUT` builds an all-fields patch, drop the read-merge `applyEdit`; add single-line title validation (line breaks/control chars → the existing 400 path) covering create, update, and rename
- [x] 2.3 Rework `ObsidianTaskRepository.update` into one `bridge.write`: pull, scan, revision check (stale → conflict error, nothing written), locate task, merge patch against freshly parsed state, edit, commit with an intent-derived message (delete the `updateMessage` diffing heuristic and the mutable message var; drop the lambda `write` overload if unused); `changeTopic` validation uses `scan.topics` in the same scan
- [x] 2.4 Contract-test both backends with the same suite: each patch field changes exactly its field against fresh state, unnamed fields survive a concurrent change, unknown id fails, unknown topic fails, null-due patch clears, revision mismatch aborts

## 3. Surgical editor fixes

- [x] 3.1 Preserve line terminators: detect the file's dominant terminator and use it when rejoining; CRLF file round-trips byte-identically outside edited lines
- [x] 3.2 Normalize-on-edit for inline checkbox-line fields: unedited inline fields stay in place byte-identically (done flip only swaps the checkbox char); a patched inline field, or a title rewrite, migrates the affected fields to their own indented follow-up lines with no duplicates
- [x] 3.3 A due-date patch always rewrites or removes the `[due::]` line even when the current value is unparseable; a patch without dueDate never touches it
- [x] 3.4 Escape field values on write (`Regex.escapeReplacement` for topic); make `editCheckboxLine` failure abort the write with an error instead of silently reporting success
- [x] 3.5 Topic relocation moves the full widened block (notes travel along; nothing remains in the source file)
- [x] 3.6 Unit/integration-test the editor against local temp repos: CRLF preservation, inline-field normalization without duplicates, unparseable due cleared, `$`-topic write, note line travels on relocation, loud failure on unmatchable checkbox line, one-commit-per-op and no-op-no-commit still hold

## 4. Revision token surface

- [x] 4.1 Expose the vault revision from `VaultGitBridge` (clone HEAD after pull) and a counter from the in-memory backend; thread it through reads so every task result knows the revision it was read at
- [x] 4.2 Add optional `revision` to task responses and mutation requests in `shared/` transfer models (reschedule body, new optional bodies for complete/reopen, `PUT`, delete); omitted revision skips the check
- [x] 4.3 Map the stale-revision failure to `409` with the standard error body in StatusPages; routes pass the supplied revision through; mutation responses carry the new revision
- [x] 4.4 Integration tests under `routes/`: stale revision → 409 and nothing written, current revision succeeds with newer revision returned, chained mutations using returned revisions, omitted revision behaves as before

## 5. Assistant tools

- [x] 5.1 Edit tools accept and pass the revision, surface the new revision from results, and render the 409/conflict error readably; update the system prompt with the re-list-and-retry loop
- [x] 5.2 Unit-test tool wiring without an LLM: revision threading, stale-revision error message, sequential edits chaining revisions

## 6. Documentation and sync

- [x] 6.1 Update README.md (revision token contract, inline-field support, single-pull edits) and CLAUDE.md's planned-directions note if stale; ROADMAP.md untouched items stay

## 7. Review remediation (post-implementation code review)

A high-effort review of the implemented change surfaced ten findings plus one wiring gap; all are folded back in here before archiving. Each is fixed test-first.

- [x] 7.1 (Finding 1) `VaultGitBridge.write` must not relabel deterministic editor/logic failures as `VaultConflictException`: let the grammar-mismatch/edit-abort error (task 3.4's "loud failure") propagate as its own error instead of being caught by the broad `catch (Exception)` and reported as a 409 retry loop; only git/rebase/push failures map to a conflict
- [x] 7.2 (Finding 2) `TaskRoutes.revisionBody` must distinguish an absent body (revision null, proceed) from a malformed body (fail with 400) instead of `runCatching{…}.getOrNull()` swallowing both and silently dropping the guard — reads the revision only when a body with a `Content-Type` is declared; malformed JSON then 400s
- [x] 7.3 (Finding 3) Preserve line terminators on the append/relocation write paths too: `appendUnderTasksHeading` and the relocation append must use the destination file's dominant terminator, not a hardcoded `\n` (completes task 3.1 for create + relocation)
- [x] 7.4 (Finding 4) Make topic relocation symmetric: relocate whenever the resolved target file differs from the task's current file (including inbox → topic file), removing the `fileTopic != null` special case so `update` placement matches `create`
- [x] 7.5 (Finding 5) Take `InMemoryTaskRepository.findAll`/`findById` snapshot + revision under the lock so data and revision are paired atomically, matching `update`/`delete`
- [x] 7.6 (Finding 6) Validate the topic inside the repository's single pull+write section (against the fresh scan's registry + topic-file targets) and drop the service's separate `listTopics()` pull, eliminating the second pull and the TOCTOU (completes task 2.3's intent)
- [x] 7.7 (Finding 7 + reuse) Loosen the tasks-heading match (case-insensitive, tolerate a single `#` and trailing decoration) and hoist it to one shared compiled pattern instead of two inline copies, so an existing section is reused rather than duplicated
- [x] 7.8 (Finding 8) Skip the in-memory revision increment when a patch yields an identical task, so "no change → same revision" holds on both backends; add the no-op case to the shared contract test
- [x] 7.9 (Finding 9, efficiency) Thread the already-scanned source file content through so `ObsidianTaskRepository.update` stops re-reading the file from disk after the scan already loaded it
- [x] 7.10 (Finding 10, reuse) Route `formatBlock` through the shared `VaultTaskGrammar`/field-indent definitions instead of re-implementing bold-title and field syntax, so create and edit cannot drift
- [x] 7.11 (Review discussion) Wire the web client to the revision contract: `TasksScreenModel` threads each task's `revision` into `UpdateTaskRequest`, and a `409` surfaces as a "task changed, reloaded" message while the list is refreshed, instead of a silent last-writer-wins overwrite

## 8. Sync

- [x] 8.1 Verify delta specs against the implementation (including section 7), sync into `openspec/specs/`, and archive the change
