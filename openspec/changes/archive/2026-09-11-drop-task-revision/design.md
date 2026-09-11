## Context

`harden-task-edits` added an optimistic-concurrency storage revision: a `Revisioned<T>` wrapper on every read/mutation result, an optional `expectedRevision` on every mutation, a `StaleRevisionException` → `409`, a counter in the in-memory backend and a git-HEAD token in the Obsidian backend, `revision` fields on the transfer models, revision-aware agent tools, and a web-client conflict reload. It is opt-in (omitting the revision skips the check) and its only automatic consumers are the assistant and the UI.

## Goals / Non-Goals

**Goals:**
- Remove the storage-revision mechanism end to end, returning plain `Task` / `List<Task>` / `Task?` from the repository, service, routes, and tools.
- Preserve the real hardening: per-field `TaskPatch` merge onto freshly-pulled state, one pull per mutation, and git rebase/push conflict handling (`VaultConflictException` → `409`).
- Leave the build green and specs consistent.

**Non-Goals:**
- Changing the per-field patch/merge semantics or the surgical-rewrite behavior.
- Adding any alternative concurrency control. Mutations become last-writer-wins on the named field (a same-field overwrite is recoverable from git history).

## Decisions

- **Return plain values.** `TaskRepository` (`create`/`findAll`/`findById`/`update`) and `TaskService` return `Task` / `List<Task>` / `Task?` directly. `VaultGitBridge.read`/`write` return `T`; drop `headRevision()`. Delete `Revisioned.kt`.
- **Drop the staleness guard.** Remove `expectedRevision` from repository/service/route signatures, delete `StaleRevisionException` and its StatusPages mapping, and delete `RevisionRequest` plus the `revisionBody()` route helper (so the intent endpoints take no body again; `reschedule` keeps its `RescheduleTaskRequest` for the due date only).
- **Transfer models.** Remove `revision` from `TaskResponse`, `UpdateTaskRequest`, and `RescheduleTaskRequest`; `toResponse` loses its revision parameter.
- **Agent + client.** Remove the revision params and `REVISION_DESCRIPTION` from `TaskTools`, trim the system-prompt paragraph to "re-list on a vault conflict", and revert `TasksScreenModel.update` to send no revision with no `409` reload branch (the conflict still surfaces via the existing error path).
- **In-memory backend.** Drop the `AtomicLong` revision; keep a lock only where a read-modify-write needs it. The no-op-equals-no-change short-circuit is no longer observable and is removed with the revision.

## Risks / Trade-offs

- **Lost capability:** concurrent edits to the *same* field are now silently last-writer-wins. Mitigated by: different-field edits still merge safely (per-field patch on fresh state), and git history preserves any overwritten value. Acceptable for a single-user vault.
- **Reverts recent work:** this undoes the `revision` additions (and the review fixes that existed only because of them). That code is removed rather than maintained — a net simplification.
