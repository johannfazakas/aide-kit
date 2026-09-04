## MODIFIED Requirements

### Requirement: Task management screen
The web client SHALL provide a task screen that lists the user's tasks and supports creating, editing (full-replace, including completion), and deleting tasks through the service's REST API, labeling the task's grouping as its topic. The create and edit forms SHALL offer the topic as a selection from the known topics fetched via `GET /api/v1/topics`, plus a no-topic choice — never as free text. Deletion SHALL require an in-app confirmation before the request is sent. The screen SHALL provide a text filter that narrows the visible list client-side, and SHALL re-fetch the list when the screen is navigated to as well as on an explicit refresh action. Server errors for unsupported operations (such as edit or delete against Obsidian storage) SHALL surface through the screen's existing error affordance.

#### Scenario: Listing and filtering
- **WHEN** the user opens the task screen and types into the filter field
- **THEN** the current tasks are fetched from the service and the visible list narrows to tasks matching the filter text

#### Scenario: Creating a task
- **WHEN** the user submits the create form with a title (and optional due date and a topic picked from the fetched list, or no topic)
- **THEN** the task is created via `POST /api/v1/tasks` and appears in the list

#### Scenario: Topic choices come from the service
- **WHEN** the user opens the create form's topic selection
- **THEN** the choices are exactly the topics returned by `GET /api/v1/topics` plus a no-topic option, with no way to type an arbitrary topic

#### Scenario: Completing a task via edit
- **WHEN** the user marks a listed task as completed
- **THEN** the task is updated via the full-replace `PUT` with its other fields preserved and the list reflects the change

#### Scenario: Deleting with confirmation
- **WHEN** the user asks to delete a task and confirms the dialog
- **THEN** the task is deleted via `DELETE /api/v1/tasks/{id}` and leaves the list; cancelling the dialog sends no request

#### Scenario: Refresh after assistant changes
- **WHEN** tasks were changed through the chat assistant and the user navigates to the task screen or presses refresh
- **THEN** the list reflects the current server state

#### Scenario: Unsupported operation surfaced
- **WHEN** the service responds `501` to an edit or delete because Obsidian storage does not support it yet
- **THEN** the screen shows the error message and the list remains unchanged
