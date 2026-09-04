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
The system SHALL parse tasks deterministically on every request (no cache, no database) from the vault clone's task files only: files declaring `topic:` in YAML frontmatter, plus the Inbox capture file — checkbox lines in any other note (process, plan, research files) SHALL NOT be indexed. A task is a markdown checkbox line (title) with optional Dataview inline fields on indented lines beneath it — `[due::]` (`yyyy-MM-dd`, optional 24h ` HH:mm`), `[topic::]`, `[id::]`, and `[recurrence::]` (recognized only to exclude templates, never exposed); any other inline field line — including `[rid::]` — SHALL be treated as part of the task block and ignored, so recurrence instances appear as plain tasks. A task's title SHALL be the checkbox line's text with surrounding markdown emphasis markers stripped, so bolded and plain titles yield the same domain value. A task's effective topic SHALL be the inline `[topic::]` when present, else the containing file's frontmatter `topic:` value, else none. Checkbox state SHALL map to `done`: `[ ]` is open; `[x]` and `[-]` are done. Recurrence templates (tasks with `[recurrence::]` and no `[due::]`) SHALL be excluded from all list and get results. Parsing SHALL be lenient: unparseable task lines are skipped and malformed field values degrade to absent fields; no vault content may fail a request.

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

### Requirement: Task identity
A task's id SHALL be the value of its `[id::]` inline field when present; otherwise the system SHALL derive a deterministic short fixed-length hash token from the task's canonical content (relative file path, title, field values, occurrence index), stable across rescans while the task is unchanged. Id lookup SHALL match explicit `[id::]` values first and consider derived ids only among tasks without one. Fetching a derived id whose task has since changed SHALL yield not-found. Tasks created by the system SHALL always be written with an explicit `[id::]` in the same token format (derived from random input), unique within the vault. The system SHALL log at INFO the presence of tasks lacking an explicit id, and SHALL log a warning when a derived id collides with an explicit one.

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
Task update and delete SHALL fail in Obsidian mode with a distinct domain error stating the operation is not yet supported for Obsidian storage. The error SHALL surface as a clear REST error response and as a readable tool error to the assistant; it SHALL NOT crash the request or the chat run.

#### Scenario: Update rejected
- **WHEN** an update is attempted while Obsidian storage is active
- **THEN** the caller receives an error explaining updates are not yet supported for Obsidian storage and no vault content changes

### Requirement: Build isolation from git and network
No build or test step SHALL contact a git remote, GitHub, or a real vault. Git-backed behavior SHALL be verified against locally created temporary repositories, and `./gradlew build` SHALL remain runnable offline (beyond dependency resolution) and Docker-free.

#### Scenario: Offline build
- **WHEN** `./gradlew build` runs without network access to any vault remote
- **THEN** all tests pass, exercising git behavior only against local temporary repositories
