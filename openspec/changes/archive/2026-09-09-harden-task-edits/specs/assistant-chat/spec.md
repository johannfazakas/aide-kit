# Delta: assistant-chat

## MODIFIED Requirements

### Requirement: Task management through the assistant
The assistant SHALL be able to list tasks (optionally filtered by topic), list the known topics, retrieve a task by id, create tasks, and edit tasks through intent-specific tools backed by the existing task service: complete, reopen, reschedule (set or clear the due date), rename, and change topic. Each edit tool SHALL take the task id plus only the value being changed; the change is applied as a patch on top of the task's freshly read state, so the assistant never supplies — and can never accidentally erase — fields it is not changing. The edit tools SHALL carry the storage revision from the assistant's most recent read of the task, and each successful edit SHALL make the returned new revision available for subsequent edits; when an edit fails because the store changed (stale revision or vault conflict), the assistant SHALL re-list to refresh its view and either retry the edit against the fresh state or, when the task no longer matches the user's intent, tell the user what changed. A created task's topic, and the target topic of a topic change, must be one of the known topics or absent; when the user names a topic outside the list, the assistant SHALL consult the topics tool and clarify with the user (suggesting close matches or offering to capture without a topic) rather than guessing or inventing a topic. The assistant SHALL NOT be able to delete tasks and SHALL NOT be given a full-replace update tool.

#### Scenario: Creating a task via natural language
- **WHEN** the user asks the assistant to add a task with a given title
- **THEN** the task is created through the task service and the reply confirms the created task

#### Scenario: Listing tasks via natural language
- **WHEN** the user asks the assistant what tasks exist (optionally for a topic)
- **THEN** the assistant invokes the list tool and the reply reflects the tasks currently in the store

#### Scenario: Completing a task via natural language
- **WHEN** the user asks the assistant to mark an identified task as done
- **THEN** the complete tool is invoked with the task's id, the task ends up `done` with every other field unchanged, and the reply confirms it

#### Scenario: Reopening a task via natural language
- **WHEN** the user asks the assistant to reopen a task that is done
- **THEN** the reopen tool is invoked and the task ends up not done with every other field unchanged

#### Scenario: Rescheduling a task via natural language
- **WHEN** the user asks the assistant to move a task to another day
- **THEN** the reschedule tool is invoked with the resolved date and the task carries the new due date with every other field unchanged

#### Scenario: Renaming a task via natural language
- **WHEN** the user asks the assistant to rename an identified task
- **THEN** the rename tool is invoked and the task carries the new title with every other field unchanged

#### Scenario: Changing a task's topic via natural language
- **WHEN** the user asks the assistant to move a task to another known topic
- **THEN** the change-topic tool is invoked and the task ends up under the new topic

#### Scenario: No deletion capability
- **WHEN** the user asks the assistant to delete a task
- **THEN** no task is deleted and the reply explains the assistant cannot delete tasks

#### Scenario: Unknown topic clarified instead of guessed
- **WHEN** the user asks for a task under a topic that is not in the known-topics list
- **THEN** no task is created or moved with that topic and the reply asks the user to pick a known topic (or none), naming close matches when they exist

#### Scenario: Capture without a topic
- **WHEN** the user asks the assistant to add a task and no topic is given or agreed
- **THEN** the task is created without a topic and the reply confirms it landed in the inbox for later grooming

#### Scenario: Vault conflict surfaces readably
- **WHEN** Obsidian storage is active and an edit fails because the vault has conflicting edits
- **THEN** the tool returns the backend's error, no vault content changes, and the reply explains the task was not saved

#### Scenario: Stale view refreshed before retrying
- **WHEN** an edit tool fails because the store changed since the assistant's last listing
- **THEN** the assistant re-lists the tasks and either completes the instruction against the fresh state or explains to the user what changed

#### Scenario: Sequential edits chain revisions
- **WHEN** the user asks the assistant to edit several tasks in one instruction
- **THEN** each successful edit's returned revision is used for the next, without a re-list between them
