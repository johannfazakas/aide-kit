## Context

The Obsidian backend supports list/get/create; `update` and `delete` throw the unsupported-operation error. `TaskRepository.update(id, title, dueDate, topic, done)` is a full-replace contract already implemented by `InMemoryTaskRepository` and exercised by `PUT /tasks/{id}`. The indexer parses task blocks leniently and ignores lines it doesn't model (`[rid::]`, `[estimate::]`, free text) — those lines exist in the real vault and must survive edits. Tasks without an explicit `[id::]` are addressed by a content-derived hash id that changes when the task changes.

## Goals / Non-Goals

**Goals:**

- Full-replace `update` works against the vault without touching anything the model doesn't own.
- Assistant can complete, reopen, reschedule, rename, and change the topic of a task via intent tools, with no risk of erasing fields by omission.
- UI-friendly intent endpoints for the most common operations.

**Non-Goals:**

- Delete (deliberately absent from both API behavior in Obsidian mode and agent tools).
- Recurrence awareness, exposing due times or `[estimate::]`, persistent conversation memory.
- Client/UI changes consuming the new endpoints.
- Cancelled state (`[-]`) writable via the API — reopen clears it, but nothing sets it.

## Decisions

**Full-replace repository contract, intent merge in the service.** The repository keeps `update(id, title, dueDate, topic, done)`; intent operations (complete, reopen, reschedule, rename, change topic) are `TaskService` methods that load the current task, substitute the changed field, and call `update` with full state. Alternative — surgical per-field repository operations — was rejected to keep one mutation contract across both backends; safety is achieved at the write layer instead (see next decision). The agent tools and intent endpoints call the service intent methods; `PUT` maps straight to the service's full update.

**Changed-fields-only block rewrite.** `ObsidianTaskRepository.update` locates the task's block, compares the requested state against the parsed current state, and rewrites only the lines backing fields whose values differ: the checkbox/title line for `done`/`title`, the `[due::]` line for the due date, `[topic::]`/file location for topic. All other lines in the block — unknown inline fields, free text, ordering, emphasis — are carried over byte-identically. Consequence: completing a task with `[due:: 2026-09-05 14:00]` never touches the due line, so the unexposed time survives. Rescheduling writes date-only (the model has no times yet) — accepted limitation, already a roadmap item. Alternative — re-serializing the whole block through the create writer — was rejected because it silently destroys `[rid::]`/`[estimate::]` and hand formatting.

**Id stamped on first mutation, reusing the derived id.** When the task being updated has no explicit `[id::]`, the update writes the task's current derived id — the very value the caller addressed it by — into an explicit `[id::]` line in the same commit. The id therefore never changes: without this, mutating the content would shift the derived hash and orphan the id the caller (and any conversation history) holds. Alternative — minting a fresh random token as create does — was rejected for exactly that reason. The existing collision warning (derived id equal to another task's explicit id) covers the rare clash. A stale derived id (task changed since listing) remains the existing not-found path.

**Topic change: relocate only from dedicated topic files.** If the containing file declares `topic:` in frontmatter, the block is removed from it and appended under the target topic file's `## Tasks` heading using the create routing rules (registry topic without a dedicated file, or no topic → Inbox; inline `[topic::]` written only when not inherited from the destination). One commit touches both files. If the containing file declares no frontmatter topic (Inbox today; local tasks inside arbitrary notes later), the block stays where it is and only its inline `[topic::]` is written or rewritten. Rationale: files with a frontmatter topic are the per-topic homes worth keeping tidy; anything else owns its own placement. Unknown topics are rejected by the existing registry validation before routing.

**Write flow mirrors create.** Updates run under the same serialization as create: pull, edit, one commit per operation with a message naming the action and task title, push; on rebase/push failure the clone resets to remote state and the caller gets the existing "vault has conflicting edits, not saved" error. The pulled state is re-scanned before editing so the update applies against the freshest block (an update whose target vanished after the pull is not-found, not a corrupted write).

**REST surface.** `PUT /tasks/{id}` unchanged (full replace, now supported in Obsidian mode). New: `POST /tasks/{id}/complete` and `POST /tasks/{id}/reopen` (no body), `POST /tasks/{id}/reschedule` with a body carrying the due date where an explicit null clears it. All return the updated task; unknown id → existing 404 shape. `DELETE` keeps the unsupported error in Obsidian mode.

**Agent tools.** Five new tools in `TaskTools` — complete, reopen, reschedule, rename, change topic — each taking the task id plus only the changed value, going through `TaskService`. The generic full-update tool is not exposed to the agent; there is still no delete tool. Reopen maps both `[x]` and `[-]` to `[ ]`.

## Risks / Trade-offs

- [Reschedule drops an existing due time because the model is date-only] → Accepted; surfacing times is a separate roadmap item, and unchanged due dates never rewrite the line.
- [Derived-id addressing can go stale between the assistant's list and the update] → Existing not-found error surfaces to the agent as a readable tool error; the agent re-lists and retries. Id stamping makes this a first-edit-only problem per task.
- [Two-file topic-change commit has a larger conflict surface] → Same reset-and-report discipline as create; the operation is atomic within one commit so no half-moved task can be pushed.
- [Concurrent hand-edits between pull and push] → Serialized mutations plus rebase-fail-reset already cover this for create; updates inherit the same path.
- [In-memory and Obsidian backends drifting on update semantics] → `TaskService` merge logic is backend-agnostic and unit-tested; repository contract tests cover both implementations.
