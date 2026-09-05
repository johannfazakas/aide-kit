## Why

Tasks stored in the Obsidian vault are read-and-create only: any update attempt fails with "not yet supported for Obsidian storage", so daily flows like marking a task done, rescheduling it, or moving it to another topic still require hand-editing the vault. Edit support is the top deferred follow-up on the roadmap and unblocks the assistant's most common daily operations.

## What Changes

- The Obsidian backend implements `update`: surgical rewrite of the existing task block that changes only the lines whose field values actually changed and carries everything else over byte-identically (unknown inline fields like `[rid::]`/`[estimate::]`, formatting, free text). The "update unsupported in Obsidian mode" error goes away (delete stays unsupported).
- Tasks addressed by a derived id get an explicit `[id::]` stamped as part of their first mutation, reusing that same derived id so the task's id never changes; later edits address it stably.
- Topic changes relocate the task only when it lives in a dedicated topic file (frontmatter `topic:`): the block moves to the new topic's file via the same routing as create. In files without a frontmatter topic (Inbox, future local tasks), the task stays in place and only its inline `[topic::]` is rewritten.
- Vault mutations for update follow the create discipline: serialized, pull → edit → commit → push, one commit per operation with a message naming the action, reset-and-fail-clearly on conflict.
- New intent REST endpoints for UI use: `POST /tasks/{id}/complete`, `POST /tasks/{id}/reopen`, `POST /tasks/{id}/reschedule` (due date in body, null clears). `PUT /tasks/{id}` keeps its full-replace contract.
- New intent-specific assistant tools: complete, reopen, reschedule, rename, and change-topic. Each passes only its intent; `TaskService` loads the current task and merges into a full update, so the LLM never erases a field by omission. The assistant still has no delete tool.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `task-management`: update becomes supported in Obsidian mode; new intent endpoints (complete, reopen, reschedule) alongside the existing full `PUT`.
- `obsidian-task-storage`: the "unsupported operations" requirement narrows to delete only; new requirements for surgical block rewrite, id-stamping on first mutation, topic-change relocation rules, and the update commit/conflict discipline.
- `assistant-chat`: task edits via natural language (complete, reopen, reschedule, rename, change topic) replace the "update not supported by the backend" behavior; no-deletion stance unchanged.

## Impact

- `service/`: `ObsidianTaskRepository` (+ vault block editing), `TaskService` (merge logic for intent operations), `TaskRoutes` (new endpoints), `agent/TaskTools` (new tools), agent system prompt.
- Tests: unit tests for block rewriting and merge semantics, integration tests under `routes/`, git behavior against local temp repos only; nothing calls an LLM.
- `README.md` and `ROADMAP.md` (drop the implemented follow-up).
- No client/UI work in this change; the new endpoints are there for the UI to adopt later.
