# Delta: obsidian-task-storage

## MODIFIED Requirements

### Requirement: Unsupported operations in Obsidian mode
Task delete SHALL fail in Obsidian mode with a distinct domain error stating the operation is not yet supported for Obsidian storage. The error SHALL surface as a clear REST error response and as a readable tool error to the assistant; it SHALL NOT crash the request or the chat run.

#### Scenario: Delete rejected
- **WHEN** a delete is attempted while Obsidian storage is active
- **THEN** the caller receives an error explaining deletes are not yet supported for Obsidian storage and no vault content changes

## ADDED Requirements

### Requirement: Task update in the vault
Updating a task in Obsidian mode SHALL execute under the same discipline as create, serialized so no two vault mutations interleave: update the clone from the remote, re-locate the task in the fresh state, apply the edit, commit with a message identifying the action and the task, and push. Each update SHALL produce exactly one commit, even when it touches two files (topic relocation). When the task cannot be found after the refresh — including a derived id whose task has since changed — the operation SHALL fail with the existing not-found error and write nothing. On rebase or push failure the system SHALL restore the clone to the remote state, leave no partial changes or conflict markers, and fail the operation with an error stating the vault has conflicting edits and the change was not saved.

#### Scenario: Successful update reaches the remote
- **WHEN** an update succeeds
- **THEN** the vault remote contains exactly one new commit whose diff touches only the task's changed lines

#### Scenario: Conflicting concurrent edit
- **WHEN** the push is rejected because the remote advanced with a conflicting change
- **THEN** the clone is reset to the remote state, the caller receives a clear "not saved" error, and no conflict markers exist in the clone

#### Scenario: Target vanished before the edit
- **WHEN** an update is attempted for a task that was removed from the vault after it was listed
- **THEN** the operation fails with the not-found error and no commit is created

### Requirement: Surgical task block rewrite
An update SHALL rewrite only the lines backing fields whose requested value differs from the task's parsed current state — the checkbox/title line for `done` and title, the `[due::]` line for the due date, the `[topic::]` line or file placement for the topic — and SHALL carry every other line of the task block over byte-identically: unrecognized inline fields (such as `[rid::]` and `[estimate::]`), free-text lines, line ordering, and title emphasis when the title is unchanged. A rewritten `[due::]` line SHALL carry the date only; a due line whose date is unchanged SHALL NOT be rewritten, so an unexposed time component survives edits to other fields.

#### Scenario: Unknown inline fields survive completion
- **WHEN** a task whose block contains `[rid::]` is marked done
- **THEN** only the checkbox character changes and the `[rid::]` line is byte-identical

#### Scenario: Due time survives completion
- **WHEN** a task with `[due:: 2026-09-05 14:00]` is marked done without changing its due date
- **THEN** the due line is byte-identical after the update

#### Scenario: Reschedule rewrites only the due line
- **WHEN** a task's due date is changed
- **THEN** the `[due::]` line carries the new date and every other line of the block is byte-identical

### Requirement: Id stamping on first mutation
When an update addresses a task that has no explicit `[id::]`, the system SHALL write the task's current derived id — the same value the caller addressed it by — into an explicit `[id::]` line as part of the same commit, and the operation SHALL return the task with that id. The task's id therefore never changes when it is stamped, and subsequent operations SHALL address the task by the same id even though its content has changed.

#### Scenario: Derived-id task stamped on completion
- **WHEN** a task without `[id::]` is marked done via its derived id
- **THEN** the committed block contains an `[id::]` line carrying exactly that derived id, and the response carries the same id

#### Scenario: Stamped id stable afterwards
- **WHEN** a task stamped by a previous mutation is listed again
- **THEN** it exposes the stamped id, unchanged by rescans

### Requirement: Topic change placement
When an update changes a task's topic and the task's containing file declares a `topic:` in YAML frontmatter, the system SHALL move the task block out of that file and append it via the create routing rules (the new topic's dedicated file's `## Tasks` heading; Inbox with inline `[topic::]` when the registry topic has no dedicated file; inline `[topic::]` written only when the destination does not inherit it) — as a single commit. When the containing file declares no frontmatter `topic:` (such as the Inbox), the task SHALL remain in place and only its inline `[topic::]` field SHALL be written or rewritten. Topic validation against the registry applies before any routing, as for create.

#### Scenario: Moved out of a dedicated topic file
- **WHEN** a task inside a file with frontmatter `topic: family` is changed to topic `health`, which has a dedicated file
- **THEN** one commit removes the block from the family file and appends it (with preserved lines) under the health file's `## Tasks` heading

#### Scenario: Inbox task keeps its place
- **WHEN** an Inbox task is assigned topic `health`
- **THEN** the task stays in the Inbox file and carries an inline `[topic:: health]`

#### Scenario: Unknown topic rejected before routing
- **WHEN** a topic change names a topic absent from the registry
- **THEN** the operation fails validation and no vault content changes
