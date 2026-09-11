# Spec: obsidian-task-storage

## Purpose

Requirements for the vault-backed task storage: a private git clone of an Obsidian vault, deterministic markdown task indexing, task identity, read/write synchronization against the remote, and the created-task format — active when the service runs in Obsidian storage mode.
## Requirements

### Requirement: Vault clone lifecycle
When the storage mode is Obsidian, the system SHALL maintain a private git clone of the configured vault repository (HTTPS remote, personal access token authentication, configurable branch defaulting to `main`), cloning it at startup when absent and reusing it otherwise. The system SHALL NOT read or write any externally managed vault directory — all vault access goes through its own clone.

#### Scenario: Clone created on first startup
- **WHEN** the service starts in Obsidian mode and the configured clone directory does not contain a clone
- **THEN** the vault repository is cloned there before the service serves task requests

#### Scenario: Existing clone reused
- **WHEN** the service restarts and the clone directory already holds a valid clone
- **THEN** the existing clone is reused (updated by pulling) rather than re-cloned

### Requirement: Obsidian mode configuration fail-fast
Obsidian storage mode SHALL require the vault repository URL and access token from the environment (`OBSIDIAN_REPO_URL`, `OBSIDIAN_REPO_TOKEN`). When Obsidian mode is selected and either is missing, the service SHALL refuse to start with an error naming the missing variable, consistent with the existing LLM-key fail-fast behavior.

#### Scenario: Missing repo configuration aborts startup
- **WHEN** the service starts with `APP_PROFILE=live` and no `OBSIDIAN_REPO_URL`
- **THEN** it exits with an error naming the variable and serves no requests

### Requirement: Markdown task indexing
The system SHALL parse tasks deterministically on every request (no cache, no database) from the vault clone's task files only: files declaring `topic:` in YAML frontmatter, plus the Inbox capture file — checkbox lines in any other note (process, plan, research files) SHALL NOT be indexed. A task is a markdown checkbox line with optional Dataview inline fields — recognized both on the checkbox line itself and on the indented lines beneath it — `[due::]` (`yyyy-MM-dd`, optional 24h ` HH:mm`), `[topic::]`, `[id::]`, and `[recurrence::]` (recognized only to exclude templates, never exposed); any other inline field — including `[rid::]` — SHALL be treated as part of the task block and ignored, so recurrence instances appear as plain tasks. A task's block SHALL comprise the checkbox line plus all consecutive indented non-blank lines beneath it, whether they carry fields or free text. A task's title SHALL be the checkbox line's text with inline field expressions removed and surrounding markdown emphasis markers stripped, so bolded and plain titles yield the same domain value; when the same field key appears more than once, the first occurrence (checkbox line first, then follow-up lines in order) wins. A task's effective topic SHALL be the inline `[topic::]` when present, else the containing file's frontmatter `topic:` value, else none. Checkbox state SHALL map to `done`: `[ ]` is open; `[x]` and `[-]` are done. Recurrence templates (tasks with `[recurrence::]` and no `[due::]`) SHALL be excluded from all list and get results. Parsing SHALL be lenient: unparseable task lines are skipped and malformed field values degrade to absent fields; no vault content may fail a request.

#### Scenario: Topic inherited from frontmatter
- **WHEN** a file with frontmatter `topic: family` contains a plain checkbox task with no inline topic
- **THEN** the task is listed with topic `family`

#### Scenario: Inline topic overrides frontmatter
- **WHEN** a task inside that file carries `[topic:: health]`
- **THEN** the task is listed with topic `health`

#### Scenario: Cancelled task counts as done
- **WHEN** the vault contains a task marked `[-]`
- **THEN** it is listed with `done` true

#### Scenario: Recurrence template hidden
- **WHEN** a file contains a template task with `[recurrence::]` and no `[due::]`, followed by dated instances carrying the same `[rid::]`
- **THEN** the template is absent from list results while the instances appear as plain tasks

#### Scenario: Malformed field degrades gracefully
- **WHEN** a task carries `[due:: next tuesday]`
- **THEN** the task is still listed, with no due date, and the request succeeds

#### Scenario: Non-task notes invisible
- **WHEN** a plan note without frontmatter `topic:` contains checkbox action items
- **THEN** none of them appear in list results

#### Scenario: Inbox tasks listed without topic
- **WHEN** the Inbox file contains open tasks
- **THEN** they are listed with a null topic

#### Scenario: Inline field on the checkbox line parsed
- **WHEN** the vault contains `- [ ] Pay rent [due:: 2026-09-01]`
- **THEN** the task is listed with title `Pay rent` and due date `2026-09-01`

#### Scenario: Field below a note line still parsed
- **WHEN** a task block is checkbox line, an indented free-text note, then an indented `[due::]` line
- **THEN** the task is listed with that due date and the note line belongs to its block

### Requirement: Task identity
A task's id SHALL be the value of its `[id::]` inline field when present; otherwise the system SHALL derive a deterministic short fixed-length hash token from the task's canonical content (relative file path, parsed title, field values, occurrence index), stable across rescans while the task is unchanged. Id lookup SHALL match explicit `[id::]` values first and consider derived ids only among tasks without one. Fetching a derived id whose task has since changed SHALL yield not-found. Tasks created by the system SHALL always be written with an explicit `[id::]` in the same token format (derived from random input), unique within the vault. The system SHALL log at INFO the presence of tasks lacking an explicit id, and SHALL log a warning when a derived id collides with an explicit one. Known limitation: when byte-identical tasks without explicit ids share a file, editing one may reassign or collide the others' derived ids (their occurrence index shifts); a re-list exposes their current ids. A parser upgrade that changes what is parsed (such as inline checkbox-line fields) re-derives the ids of affected unstamped tasks at the deploy boundary; nothing persists derived ids across a restart, so stale handles simply resolve to not-found.

#### Scenario: Explicit id wins
- **WHEN** a task carries `[id:: f3k2a]`
- **THEN** list and get expose exactly `f3k2a` as its id

#### Scenario: Derived id stable across rescans
- **WHEN** a task without `[id::]` is listed twice with no vault change in between
- **THEN** both listings expose the same id

#### Scenario: Stale derived id
- **WHEN** a task without `[id::]` is listed, then edited in Obsidian, and the previously returned id is fetched
- **THEN** the response is not-found (the existing 404 / tool-error path)

#### Scenario: Explicit id wins a lookup collision
- **WHEN** an unmarked task's derived hash happens to equal another task's explicit `[id::]` value and that id is fetched
- **THEN** the task carrying the explicit `[id::]` is returned and a warning is logged

#### Scenario: Normalization does not shift a derived id
- **WHEN** a task with an inline checkbox-line field is edited so the field moves to its own line, without the parsed values changing
- **THEN** rescans expose the same id as before the edit (the id is stamped by the mutation in any case)

### Requirement: Read synchronization
List and get operations SHALL update the clone from the remote before scanning. When the remote cannot be reached or the update fails, reads SHALL serve the last local state and record the failure, rather than failing the request.

#### Scenario: Remote change visible
- **WHEN** a task is added to the vault from another device and pushed, and the service then handles a list request
- **THEN** the new task appears in the response

#### Scenario: Offline read
- **WHEN** the remote is unreachable during a list request
- **THEN** the response contains the tasks from the current local clone and the request succeeds

### Requirement: Task creation in the vault
Creating a task in Obsidian mode SHALL execute, serialized so no two vault mutations interleave: update the clone from the remote, append the formatted task to the resolved target file, commit with a message identifying the agent action, and push. Each create SHALL produce exactly one commit. On rebase or push failure the system SHALL restore the clone to the remote state, leave no partial changes or conflict markers, and fail the operation with an error stating the vault has conflicting edits and the task was not saved.

#### Scenario: Successful create reaches the remote
- **WHEN** a create succeeds
- **THEN** the vault remote contains one new commit whose diff is exactly the appended task block

#### Scenario: Conflicting concurrent edit
- **WHEN** the push is rejected because the remote advanced with a conflicting change
- **THEN** the clone is reset to the remote state, the API/tool caller receives a clear "not saved" error, and no conflict markers exist in the clone

### Requirement: Created-task format
Created tasks SHALL be written exactly in the vault's convention: the checkbox title line with the title in markdown bold (`- [ ] **<title>**`), then indented field lines in the order `due` → `id` → `topic`, omitting absent fields, omitting `[topic::]` when the target file's frontmatter already declares that topic, and using 24h times only. The task SHALL be appended under the target file's `## Tasks` heading, preceded by a blank line (so a blank line separates the `## Tasks` heading from its first capture and separates consecutive captures); when the file has no such heading, it SHALL be created at the end of the file with the first capture. Existing content of the file SHALL NOT be modified otherwise.

#### Scenario: Full task appended
- **WHEN** a task with title and due date is created into a file whose frontmatter declares the task's topic
- **THEN** the appended block is the checkbox line with the title wrapped in `**`, followed by indented `[due::]` and `[id::]` lines in that order, with no `[topic::]` line

#### Scenario: Topic written when not inherited
- **WHEN** a task is created into a file whose frontmatter does not declare the task's topic
- **THEN** the appended block includes a `[topic::]` line after `[id::]`

#### Scenario: Capture heading created once and reused
- **WHEN** a task is created into a topic file without a `## Tasks` heading, and a second task is created later
- **THEN** the first capture appends `## Tasks` at the end of the file with the task beneath it, and the second task is appended under that same heading

### Requirement: Topic registry and capture routing
The list of known topics SHALL be read at request time from the vault's dedicated registry note (`organization/Topics.md` frontmatter `topics` list) — never hardcoded — and exposed through the repository's topics listing. `inbox` is not a topic: the Inbox capture file declares no frontmatter topic and its tasks have none. Topic-to-file routing SHALL resolve via the task files' frontmatter `topic:` declarations. A created task with no topic SHALL be appended to the Inbox file as a plain task (no inline `[topic::]`); a created task with a registry topic that has no dedicated file SHALL be appended to the Inbox file with an inline `[topic::]`. Topics outside the registry never reach routing — creation with an unknown topic is rejected by validation beforehand.

#### Scenario: Known topic routed to its file
- **WHEN** a task with topic `family` is created and a vault file declares `topic: family`
- **THEN** the task is appended to that file

#### Scenario: Topicless capture lands plain in the Inbox
- **WHEN** a task is created without a topic
- **THEN** it is appended to the Inbox file with no `[topic::]` line

#### Scenario: Registry topic without a dedicated file
- **WHEN** a task is created with a registry topic no task file declares in frontmatter
- **THEN** it is appended to the Inbox file with an inline `[topic::]` carrying that topic

#### Scenario: Registry updated without restart
- **WHEN** a new topic is added to the registry note and pushed to the vault
- **THEN** a subsequent create for that topic succeeds without a service restart

### Requirement: Unsupported operations in Obsidian mode
Task delete SHALL fail in Obsidian mode with a distinct domain error stating the operation is not yet supported for Obsidian storage. The error SHALL surface as a clear REST error response and as a readable tool error to the assistant; it SHALL NOT crash the request or the chat run.

#### Scenario: Delete rejected
- **WHEN** a delete is attempted while Obsidian storage is active
- **THEN** the caller receives an error explaining deletes are not yet supported for Obsidian storage and no vault content changes

### Requirement: Build isolation from git and network
No build or test step SHALL contact a git remote, GitHub, or a real vault. Git-backed behavior SHALL be verified against locally created temporary repositories, and `./gradlew build` SHALL remain runnable offline (beyond dependency resolution) and Docker-free.

#### Scenario: Offline build
- **WHEN** `./gradlew build` runs without network access to any vault remote
- **THEN** all tests pass, exercising git behavior only against local temporary repositories

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

### Requirement: Surgical task block rewrite
An update SHALL rewrite only the lines backing fields the patch names and whose value actually changes — the checkbox/title line for `done` and title, the `[due::]` line for the due date, the `[topic::]` line or file placement for the topic — and SHALL carry every other line of the task block over byte-identically: unrecognized inline fields (such as `[rid::]` and `[estimate::]`), free-text lines, line ordering, and title emphasis when the title is unchanged. The file's existing line terminators SHALL be preserved: a rewrite uses the file's dominant terminator and never normalizes untouched lines' endings. A field living on the checkbox line SHALL stay there byte-identically while unedited; when the patch changes such a field, or rewrites the title, the affected field moves to its own indented follow-up line (normalize-on-edit). A patch that names the due date SHALL always rewrite or remove the `[due::]` line — including when the current value is unparseable — while a patch that does not name it SHALL never touch it, so an unexposed time component survives edits to other fields. Values written into field lines SHALL be escaped so no character of a topic or date can corrupt the rewrite. When a task block the scanner indexed cannot be edited (its checkbox line no longer matches the shared grammar), the operation SHALL fail with an error rather than report success without writing.

#### Scenario: Unknown inline fields survive completion
- **WHEN** a task whose block contains `[rid::]` is marked done
- **THEN** only the checkbox character changes and the `[rid::]` line is byte-identical

#### Scenario: Due time survives completion
- **WHEN** a task with `[due:: 2026-09-05 14:00]` is marked done without changing its due date
- **THEN** the due line is byte-identical after the update

#### Scenario: Reschedule rewrites only the due line
- **WHEN** a task's due date is changed
- **THEN** the `[due::]` line carries the new date and every other line of the block is byte-identical

#### Scenario: CRLF file keeps its line endings
- **WHEN** a task inside a file with CRLF line endings is marked done
- **THEN** the commit diff touches only the checkbox line and every line ending in the file remains CRLF

#### Scenario: Note lines travel on topic relocation
- **WHEN** a task whose block contains an indented free-text note is moved to another topic file
- **THEN** the note line moves with the block and nothing of the block remains in the source file

#### Scenario: Inline field normalized when edited
- **WHEN** a task written as `- [ ] Pay rent [due:: 2026-09-01]` is rescheduled to `2026-09-10`
- **THEN** the checkbox line carries the title without the old field and a follow-up line carries `[due:: 2026-09-10]`, with no duplicate due field anywhere in the block

#### Scenario: Unparseable due value cleared
- **WHEN** a task carrying `[due:: someday]` is rescheduled with a null due date
- **THEN** the due field is removed from the block

#### Scenario: Topic name with regex metacharacters
- **WHEN** a task is moved to a registry topic containing `$` or `\`
- **THEN** the `[topic::]` line carries the topic verbatim and the operation succeeds

### Requirement: Id stamping on first mutation
When an update addresses a task that has no explicit `[id::]`, the system SHALL write the task's current derived id — the same value the caller addressed it by — into an explicit `[id::]` line as part of the same commit, and the operation SHALL return the task with that id. The task's id therefore never changes when it is stamped, and subsequent operations SHALL address the task by the same id even though its content has changed.

#### Scenario: Derived-id task stamped on completion
- **WHEN** a task without `[id::]` is marked done via its derived id
- **THEN** the committed block contains an `[id::]` line carrying exactly that derived id, and the response carries the same id

#### Scenario: Stamped id stable afterwards
- **WHEN** a task stamped by a previous mutation is listed again
- **THEN** it exposes the stamped id, unchanged by rescans

### Requirement: Topic change placement
When an update changes a task's topic, the system SHALL route the task by the same rules as create: it resolves the destination file for the new topic (the topic's dedicated file when one exists, else the Inbox), and when that destination differs from the task's current file it SHALL move the whole task block — removing it from the source file and appending it under the destination's `## Tasks` heading — as a single commit, with the inline `[topic::]` written only when the destination does not inherit the topic. Relocation SHALL be symmetric in both directions, so an Inbox task assigned a topic that has a dedicated file moves into that file exactly as a task created under that topic would. When the resolved destination is the task's current file (including a known topic that has no dedicated file), the block SHALL stay in place and only its inline `[topic::]` SHALL be written or rewritten. Topic validation against the registry applies before any routing, as for create.

#### Scenario: Moved out of a dedicated topic file
- **WHEN** a task inside a file with frontmatter `topic: family` is changed to topic `health`, which has a dedicated file
- **THEN** one commit removes the block from the family file and appends it (with preserved lines) under the health file's `## Tasks` heading

#### Scenario: Inbox task moved to a topic file relocates
- **WHEN** an Inbox task is assigned topic `health`, which has a dedicated file
- **THEN** the block is removed from the Inbox and appended under the health file's `## Tasks` heading, with no inline topic left behind

#### Scenario: Task moved to a fileless topic stays in place
- **WHEN** a task is assigned a known topic that has no dedicated file of its own
- **THEN** the block stays in its current file carrying an inline `[topic::]` for that topic

#### Scenario: Unknown topic rejected before routing
- **WHEN** a topic change names a topic absent from the registry
- **THEN** the operation fails validation and no vault content changes
