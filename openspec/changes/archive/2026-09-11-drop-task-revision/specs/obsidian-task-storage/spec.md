# obsidian-task-storage (delta)

## MODIFIED Requirements

### Requirement: Task update in the vault
Updating a task in Obsidian mode SHALL execute as a single serialized vault transaction — one remote synchronization per mutation, with no separate pre-read: update the clone from the remote, locate the task in the fresh state, merge the requested patch (only the fields the caller named) on top of the freshly parsed state, apply the edit, commit with a descriptive message, and push. Fields the patch does not name SHALL be taken from the freshly parsed state, so a concurrent vault edit to an unrelated field is never reverted. Each update SHALL produce exactly one commit, even when it touches two files (topic relocation). When the task cannot be found in the fresh state — including a derived id whose task has since changed — the operation SHALL fail with the existing not-found error and write nothing. On rebase or push failure the system SHALL restore the clone to the remote state, leave no partial changes or conflict markers, and fail the operation with an error stating the vault has conflicting edits and the change was not saved.

#### Scenario: Successful update reaches the remote
- **WHEN** an update succeeds
- **THEN** the vault remote contains exactly one new commit whose diff touches only the task's changed lines

#### Scenario: Conflicting concurrent edit
- **WHEN** the push is rejected because the remote advanced with a conflicting change
- **THEN** the clone is reset to the remote state, the caller receives a clear "not saved" error, and no conflict markers exist in the clone

#### Scenario: Target vanished before the edit
- **WHEN** an update is attempted for a task that was removed from the vault after it was listed
- **THEN** the operation fails with the not-found error and no commit is created

#### Scenario: Concurrent edit to an unrelated field survives
- **WHEN** a hand-edit changing a task's due date is synced to the remote, and a complete operation (patching only `done`) for the same task runs afterwards
- **THEN** the committed block carries the hand-edited due date and `done` true — the due line is not reverted
