## MODIFIED Requirements

### Requirement: Task management through the assistant
The assistant SHALL be able to list tasks (optionally filtered by topic), list the known topics, retrieve a task by id, create tasks, and update tasks (including marking them done) by invoking tools backed by the existing task service. A created task's topic must be one of the known topics or absent; when the user names a topic outside the list, the assistant SHALL consult the topics tool and clarify with the user (suggesting close matches or offering to capture without a topic) rather than guessing or inventing a topic. The assistant SHALL NOT be able to delete tasks. When the active storage backend does not support an operation (updates in Obsidian mode), the tool SHALL return the backend's not-supported error to the assistant, and the reply SHALL relay that the operation is not available yet.

#### Scenario: Creating a task via natural language
- **WHEN** the user asks the assistant to add a task with a given title
- **THEN** the task is created through the task service and the reply confirms the created task

#### Scenario: Listing tasks via natural language
- **WHEN** the user asks the assistant what tasks exist (optionally for a topic)
- **THEN** the assistant invokes the list tool and the reply reflects the tasks currently in the store

#### Scenario: Completing a task via natural language
- **WHEN** the user asks the assistant to mark an identified task as done
- **THEN** the task is updated with `done = true` through the task service and the reply confirms it

#### Scenario: No deletion capability
- **WHEN** the user asks the assistant to delete a task
- **THEN** no task is deleted and the reply explains the assistant cannot delete tasks

#### Scenario: Unknown topic clarified instead of guessed
- **WHEN** the user asks for a task under a topic that is not in the known-topics list
- **THEN** no task is created with that topic and the reply asks the user to pick a known topic (or none), naming close matches when they exist

#### Scenario: Capture without a topic
- **WHEN** the user asks the assistant to add a task and no topic is given or agreed
- **THEN** the task is created without a topic and the reply confirms it landed in the inbox for later grooming

#### Scenario: Update not supported by the backend
- **WHEN** Obsidian storage is active and the user asks the assistant to mark a task as done
- **THEN** the update tool returns the not-supported error, no vault content changes, and the reply explains that completing tasks is not available yet

### Requirement: Multi-step tool execution
The assistant SHALL complete multi-step tool flows within a single chat request: when carrying out an instruction requires several tool invocations in sequence (such as looking a task up and then updating it), it SHALL keep invoking tools after receiving tool results until the instruction is carried out. Narration text accompanying a tool call SHALL NOT end the agent run before that tool call is executed, and the assistant SHALL NOT reply that it is about to perform an action without performing it in the same request.

#### Scenario: Completing a task found by lookup
- **WHEN** the user asks the assistant to mark a task as done, identifying it by content or by reference to an earlier turn, so the assistant must first look up the task id
- **THEN** within that same request the assistant invokes the update tool after the lookup, the task's `done` becomes `true`, and the reply confirms the completed task

#### Scenario: Narration alongside a tool call
- **WHEN** the model's response after a tool result contains both narration text and a further tool call
- **THEN** the tool call is executed and the run continues, rather than the narration ending the run

#### Scenario: Plain answers still allowed
- **WHEN** the user's message needs no tool action, or the tools' results already answer it
- **THEN** the assistant replies in plain text and the run ends normally, without being forced into further tool calls
