# assistant-chat

## ADDED Requirements

### Requirement: Multi-step tool execution
The assistant SHALL complete multi-step tool flows within a single chat request: when carrying out an instruction requires several tool invocations in sequence (such as looking a task up and then updating it), it SHALL keep invoking tools after receiving tool results until the instruction is carried out. Narration text accompanying a tool call SHALL NOT end the agent run before that tool call is executed, and the assistant SHALL NOT reply that it is about to perform an action without performing it in the same request.

#### Scenario: Completing a task found by lookup
- **WHEN** the user asks the assistant to mark a task as done, identifying it by content or by reference to an earlier turn, so the assistant must first look up the task id
- **THEN** within that same request the assistant invokes the update tool after the lookup, the task's `completed` becomes `true`, and the reply confirms the completed task

#### Scenario: Narration alongside a tool call
- **WHEN** the model's response after a tool result contains both narration text and a further tool call
- **THEN** the tool call is executed and the run continues, rather than the narration ending the run

#### Scenario: Plain answers still allowed
- **WHEN** the user's message needs no tool action, or the tools' results already answer it
- **THEN** the assistant replies in plain text and the run ends normally, without being forced into further tool calls
