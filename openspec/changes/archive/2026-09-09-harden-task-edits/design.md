## Context

The add-task-edits change shipped intent edits over a full-replace `TaskRepository.update(id, title, dueDate, topic, done)`: `TaskService.applyEdit` reads the task (one pull), merges one field, and calls `update` (a second pull). A review confirmed the costs: a hand-edit synced between the two pulls is detected as a "difference" and silently rewritten back to the stale value (no git conflict fires, so the reset discipline never triggers); `complete()` and `reschedule(null)` are indistinguishable at the repository, so an unparseable `[due:: someday]` can never be cleared; each edit costs 2–3 pulls and full vault scans. Separately, the line-surgery layer trusts its inputs too much (unsanitized titles, unescaped regex replacements, LF-normalizing rewrites, blocks bounded to field lines only, a duplicate grammar that can silently no-op) and the scanner ignores Dataview-style inline fields on the checkbox line, so editing such a task duplicates the field. The owner is the only user; the vault is hand-edited in Obsidian while the assistant may be mid-conversation.

## Goals / Non-Goals

**Goals:**

- One git pull per mutation; read-merge-write happens atomically inside the vault write lock.
- Concurrent vault hand-edits to fields an intent does not name are never reverted.
- Stale caller knowledge is detected, not silently overwritten: mutations against an advanced vault fail with a clear conflict error and a fresh re-list recovers.
- Tasks using inline checkbox-line fields are read correctly and editable without duplication.
- The surgical-rewrite promises (byte-identical untouched lines, minimal diffs) hold for CRLF files, note lines, and hostile-but-legal titles and topic names.

**Non-Goals:**

- Stamping ids on twin tasks (byte-identical unstamped duplicates) — deliberately rejected for simplicity; the collision is documented as a limitation.
- Delete, recurrence awareness, exposing due times or `[estimate::]`, persistent conversation storage.
- Client/UI adoption of the revision token beyond what the shared transfer models provide.

## Decisions

**Patch contract in the repository, no service-side pre-read.** `TaskRepository.update(id, patch)` where `TaskPatch` distinguishes absent (keep) from set (possibly to null) per field — title, dueDate, topic, done. The Obsidian backend performs pull → scan → locate → merge patch against the freshly parsed state → surgical edit, all inside one `bridge.write`; the in-memory backend merges inside its `computeIfPresent`. `TaskService` intent methods construct single-field patches; `PUT` builds an all-fields patch, preserving full-replace semantics. Alternatives rejected: keeping full-replace and passing the read snapshot for comparison (still two pulls, still cannot express clear-vs-keep for unparseable dues); a transform lambda `update(id) { it.copy(...) }` (single pull and race closed, but the repository still receives full state, so clear-vs-keep stays inexpressible). The patch also carries the intent for the commit message, replacing the field-diffing `updateMessage` heuristic and the mutable `message` var threaded into `bridge.write`.

**Vault revision token for optimistic concurrency.** Read and list responses carry the vault revision — the clone HEAD after the pull that produced them (a monotonic counter in the in-memory backend). Mutation requests carry the revision the caller last saw; before editing (same lock, after the pull) the repository compares it against the fresh revision and fails with a conflict error when the vault has advanced. Mutation responses return the new revision so sequential edits need no re-list. This closes the one window git cannot see: staleness between the caller's read and the request. The push-rejection path (remote advanced between our pull and push) already aborts and resets — unchanged. Alternative rejected: no token, accepting last-writer-wins on the named field; the owner prefers explicit aborts. The assistant's tools take the revision from the last listing and re-list-and-retry on conflict; the system prompt explains this loop.

**Inline fields: read liberally, write canonically.** The scanner additionally runs the field regex over the checkbox line's remainder; fields found there are extracted and stripped from the title (first occurrence of a key wins, checkbox line first). Derived ids hash parsed values (clean title, raw due, topic), so an inline task and its normalized form hash identically — normalization never shifts an id; only the deploy boundary re-derives unstamped inline-field tasks, which nothing observes (sessions are in-memory, the UI re-fetches). On write: if the checkbox line needs no rewrite (done flip only swaps the checkbox character), inline fields stay byte-identical in place; a changed inline field, or a title rewrite, migrates the affected fields to their own indented follow-up lines. Alternative rejected: in-place rewriting of mixed title-and-field lines — it requires tokenizing arbitrary interleavings of title fragments, fields, and emphasis, exactly the line-surgery class the review showed to be error-prone.

**Block bounds include free-text lines.** A task block is the checkbox line plus all consecutive indented non-blank lines (field or free text); fields are parsed from any of them. This makes topic relocation carry note lines along and lets the editor find a `[due::]` line sitting below a note, closing the duplicate-field insertion. Signature inputs are unchanged, so existing derived ids are unaffected.

**One canonical grammar, loud failures.** The checkbox and field regexes move to a single shared definition used by both `VaultScanner` and `ObsidianTaskRepository` (bold-title serialization likewise shared with `formatBlock`). `editCheckboxLine` failing to match a line the scanner accepted becomes an error that aborts the write, not a silent no-op returning phantom success.

**Input hygiene at the right layers.** `TaskService` rejects titles containing line breaks or other control characters (the vault format is structurally single-line) with the existing `400` shape, covering rename, `PUT`, and create. `editTopicField` wraps the topic in `Regex.escapeReplacement` (topics are registry-controlled free text and may contain `$` or `\`). File rewrites detect the file's dominant line terminator and rejoin with it, preserving CRLF files byte-for-byte outside the edited lines; mixed-terminator files normalize to the dominant one (accepted, documented).

**Unparseable due values.** Under the patch contract, a patch that sets dueDate (including to null) always rewrites or removes the `[due::]` line regardless of whether the current value parsed; a patch without dueDate never touches it. This resolves the previously impossible clear while keeping "completing never touches the due line".

## Risks / Trade-offs

- [Revision token adds a required round-trip discipline to every mutation] → Mutation responses return the new revision, so sequential assistant edits chain without re-listing; the 409 error message tells the caller to re-list; single-user traffic makes conflicts rare.
- [LLM may mishandle the revision parameter] → Tools treat a missing revision as "latest known server-side is not assumed" and fail with a readable error instructing a re-list; tool descriptions and the system prompt spell out the loop; wiring is unit-tested without an LLM.
- [Deploy-boundary re-derivation of unstamped inline-field task ids] → Unobservable (nothing persists ids across restart); one-line spec note.
- [Twin tasks (byte-identical, unstamped) can collide ids after one is edited] → Accepted and documented; a re-list recovers; the owner does not create duplicate identical tasks.
- [Mixed line terminators in one file normalize to the dominant one] → Accepted; uniform-CRLF and uniform-LF files round-trip byte-identically, which covers real vaults.
- [Patch contract churns every repository/service/tool test] → The behavior-level assertions stay; only construction changes. Contract tests run against both backends to prevent drift.

## Migration Plan

Single deploy; no data migration. The vault needs no preparation: explicit ids are untouched, inline-field tasks simply parse correctly from the first post-deploy scan. Rollback is a redeploy of the previous version (the old parser re-derives the old ids; stamped ids remain valid throughout). Shared transfer models gain optional revision fields, so the existing web client keeps working without changes until it adopts them.

## Open Questions

None — contract shape, revision semantics, inline-field normalization, and the twin-limitation stance were settled with the owner during review triage.
