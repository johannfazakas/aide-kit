## 1. Service intent operations

- [x] 1.1 Add intent methods to `TaskService` (complete, reopen, reschedule, rename, changeTopic) that load the current task, merge the single change into full state, and call `repository.update`; not-found and unknown-topic errors reuse existing domain errors
- [x] 1.2 Unit-test the merge semantics (given-when-then): each intent changes exactly its field, unknown id fails, unknown topic on changeTopic fails, reschedule with null clears the due date — verified against the in-memory repository

## 2. Obsidian update implementation

- [x] 2.1 Implement task block editing in the Obsidian backend: locate the block by id in the refreshed clone, rewrite only lines whose field values differ from the parsed state, carry all other lines over byte-identically (unknown inline fields, free text, ordering, title emphasis when unchanged); unchanged due dates never rewrite the `[due::]` line
- [x] 2.2 Stamp an explicit `[id::]` when the updated task lacks one, reusing the task's current derived id so the id never changes, in the same commit, and return that id
- [x] 2.3 Implement topic change placement: move the block via create routing when the source file declares a frontmatter `topic:`, otherwise rewrite the inline `[topic::]` in place; single commit either way
- [x] 2.4 Wire `ObsidianTaskRepository.update` into the serialized mutation flow (pull → re-locate → edit → commit with action-naming message → push; reset and fail with the existing conflict error on rebase/push failure; not-found when the task vanished after refresh) and remove the update branch of the unsupported-operation error (delete keeps it)
- [x] 2.5 Unit/integration-test the vault update against local temp repos: byte-identical preservation scenarios (rid, due time), id stamping, both topic-change placements, one-commit-per-op, conflict reset, stale derived id → not-found

## 3. REST endpoints

- [x] 3.1 Add `POST /api/v1/tasks/{id}/complete`, `POST /api/v1/tasks/{id}/reopen`, and `POST /api/v1/tasks/{id}/reschedule` (body `dueDate`, null clears) to the task routes, returning the updated task; 404 for unknown ids, 400 for invalid reschedule body
- [x] 3.2 Route-level integration tests under `routes/` for the three intent endpoints and for `PUT` now succeeding in Obsidian mode (temp-repo backed), including the removed 501 behavior

## 4. Assistant tools

- [x] 4.1 Add the five intent tools to `agent/TaskTools` (complete, reopen, reschedule, rename, change topic), each taking the task id plus only the changed value and delegating to the `TaskService` intent methods; no full-update or delete tool exposed
- [x] 4.2 Update the agent system prompt for the new edit capabilities (topic validation via the topics tool before change-topic, no deletion stance unchanged) and unit-test tool wiring without calling an LLM

## 5. Documentation and sync

- [x] 5.1 Update README.md (task editing now supported in Obsidian mode, new endpoints) and ROADMAP.md (remove the updates/completion follow-up, keep delete and recurrence)
- [x] 5.2 Verify delta specs against implementation, then sync deltas into `openspec/specs/` and archive the change
