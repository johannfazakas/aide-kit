## Why

The storage-revision optimistic-concurrency feature (added by `harden-task-edits`) threads an opaque `revision` token and `409`-on-stale handling through every layer — repository, service, routes, agent tools, transfer models, and the web client (~17 files). For a single-user application backed by a git vault its value is marginal: the per-field patch merged onto freshly-pulled state already prevents concurrent edits to *different* fields from clobbering each other, and a same-field overwrite is recoverable from git history. The token is opt-in and its only automatic consumers are the non-deterministic assistant and the UI, so it earns its pervasive complexity poorly.

## What Changes

- **BREAKING (internal contract + wire)**: remove the storage-revision token end to end. Repository/service/route/tool methods return plain `Task` / `List<Task>` / `Task?` again (no `Revisioned<T>` wrapper), and mutations no longer accept an `expectedRevision`. Mutations are last-writer-wins on the named field.
- Delete `Revisioned<T>`, `StaleRevisionException` (and its `409` StatusPages mapping), and `RevisionRequest`; drop the `revision` field from `TaskResponse`, `UpdateTaskRequest`, and `RescheduleTaskRequest`.
- Remove the revision parameters and `REVISION_DESCRIPTION` from the assistant's edit tools and the revision/re-list-on-stale paragraph from the system prompt (the assistant still re-lists on a vault conflict).
- Simplify the backends: drop the in-memory revision counter and `VaultGitBridge.headRevision()`; `read`/`write` return plain values.
- Remove the web client's revision threading and `409`-reload handling in `TasksScreenModel`.
- **Keep** the per-field `TaskPatch` merge-onto-fresh-state and one-pull-per-mutation behavior, and the git rebase/push conflict handling (`VaultConflictException` → `409`).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `task-management`: remove the "Optimistic concurrency via storage revision" requirement entirely; drop the `revision` attribute from "Task representation"; mutation requests no longer accept a `revision`.
- `obsidian-task-storage`: the vault-update requirement drops the revision-mismatch conflict clause (git rebase/push conflict handling is retained).
- `assistant-chat`: the assistant's task-management requirement drops the revision-carrying and re-list-and-retry-on-stale-revision clauses (re-list on a vault conflict is retained).

## Impact

- `service/`: `TaskRepository` + both implementations, `TaskService` (all methods), `VaultGitBridge` (return types, drop `headRevision`), `TaskRoutes` + `StatusPages` (drop `revisionBody`/`409`), `agent/TaskTools` + system prompt; delete `repository/Revisioned.kt`, `exception/StaleRevisionException.kt`.
- `shared/`: drop `revision` from transfer models; delete `transfer/RevisionRequest.kt`.
- `client-core/`: `TasksScreenModel` update path loses revision + conflict reload.
- Tests: remove revision/`409`/no-op-revision cases across contract, repository, route, tool, and screen-model tests; keep the per-field-merge and vault-conflict tests.
- Reverses the `revision` additions from the archived `2026-09-09-harden-task-edits`; git behavior stays on local temp repos; nothing calls an LLM.
