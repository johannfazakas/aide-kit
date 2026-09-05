# Delta: task-management

## MODIFIED Requirements

### Requirement: Update task
The system SHALL replace an existing task via `PUT /api/v1/tasks/{id}` with the same body schema and topic validation as create (full replace: fields omitted from the body are unset, except `done` which defaults to `false`). The task's `id` MUST remain unchanged — in Obsidian mode, a task addressed by a derived id has that same id stamped as its explicit `[id::]` by its first mutation, so the id survives the content change. On success the system SHALL respond `200` with the updated task. Updates SHALL be supported by every storage backend, including Obsidian mode.

#### Scenario: Update replaces all fields
- **WHEN** a client sends `PUT /api/v1/tasks/{id}` for an existing task with a body containing a new `title` and no `topic`
- **THEN** the response is `200`, the task has the new `title`, its `topic` is unset, and its `id` is unchanged

#### Scenario: Mark task as done
- **WHEN** a client sends `PUT /api/v1/tasks/{id}` with `done` set to `true`
- **THEN** the response is `200` and the returned task has `done` `true`

#### Scenario: Update unknown task
- **WHEN** a client sends `PUT /api/v1/tasks/{id}` with an id that does not exist
- **THEN** the response is `404` with a JSON error body containing a `message`

#### Scenario: Update with invalid body rejected
- **WHEN** a client sends `PUT /api/v1/tasks/{id}` with a missing or blank `title`
- **THEN** the response is `400` with a JSON error body containing a `message`

#### Scenario: Update reaches the vault in Obsidian mode
- **WHEN** the service runs with Obsidian storage and a client sends a valid `PUT /api/v1/tasks/{id}` for an existing task
- **THEN** the response is `200` and the vault reflects the updated task

## ADDED Requirements

### Requirement: Intent task operations
The system SHALL expose intent endpoints for the common partial edits, so clients change one aspect of a task without assembling its full state: `POST /api/v1/tasks/{id}/complete` (marks done), `POST /api/v1/tasks/{id}/reopen` (marks not done), and `POST /api/v1/tasks/{id}/reschedule` with a JSON body whose `dueDate` sets the due date or clears it when null. Each SHALL apply the change on top of the task's current state, leave every other field untouched, respond `200` with the updated task, and respond `404` for an unknown id with the standard JSON error body. Reschedule SHALL reject an invalid date with `400`.

#### Scenario: Complete leaves other fields untouched
- **WHEN** a client sends `POST /api/v1/tasks/{id}/complete` for an open task that has a due date and topic
- **THEN** the response is `200` with `done` `true` and the task's title, due date, and topic unchanged

#### Scenario: Reopen a done task
- **WHEN** a client sends `POST /api/v1/tasks/{id}/reopen` for a done task
- **THEN** the response is `200` and the returned task has `done` `false`

#### Scenario: Reschedule sets a new due date
- **WHEN** a client sends `POST /api/v1/tasks/{id}/reschedule` with a valid `dueDate`
- **THEN** the response is `200` and the returned task carries the new due date with all other fields unchanged

#### Scenario: Reschedule clears the due date
- **WHEN** a client sends `POST /api/v1/tasks/{id}/reschedule` with `dueDate` null
- **THEN** the response is `200` and the returned task has no due date

#### Scenario: Intent operation on unknown task
- **WHEN** a client sends `POST /api/v1/tasks/{id}/complete` with an id that does not exist
- **THEN** the response is `404` with a JSON error body containing a `message`
