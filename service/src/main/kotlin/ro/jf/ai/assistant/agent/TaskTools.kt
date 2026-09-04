package ro.jf.ai.assistant.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ro.jf.ai.assistant.exception.TaskNotFoundException
import ro.jf.ai.assistant.exception.UnsupportedTaskOperationException
import ro.jf.ai.assistant.exception.VaultConflictException
import ro.jf.ai.assistant.service.TaskService
import ro.jf.ai.assistant.transfer.CreateTaskRequest
import ro.jf.ai.assistant.transfer.UpdateTaskRequest
import ro.jf.ai.assistant.transfer.toResponse

@LLMDescription("Tools for managing the user's tasks")
class TaskTools(
    private val service: TaskService,
) : ToolSet {
    private val json = Json

    @Tool
    @LLMDescription(
        "List the user's tasks, optionally filtered by topic. Returns a JSON array of tasks. " +
            "To find a task described by its content, list without a topic filter and match by title.",
    )
    fun listTasks(
        @LLMDescription(
            "Topic to filter by; omit to list all tasks. Only pass a topic the user explicitly named — " +
                "it is one of the known topics (see listTopics), never guessed from task content",
        )
        topic: String? = null,
    ): String =
        guarded {
            json.encodeToString(service.list(topic).map { it.toResponse() })
        }

    @Tool
    @LLMDescription(
        "List the known topics a task may be filed under. Returns a JSON array of topic name strings. " +
            "Consult this before filing a task under a topic; a task's topic must be one of these or absent.",
    )
    fun listTopics(): String = guarded { json.encodeToString(service.listTopics()) }

    @Tool
    @LLMDescription("Get a single task by its id. Returns the task as JSON.")
    fun getTask(
        @LLMDescription("The id of the task")
        id: String,
    ): String =
        guarded {
            json.encodeToString(service.get(id).toResponse())
        }

    @Tool
    @LLMDescription("Create a new task. Returns the created task as JSON, including its generated id.")
    fun createTask(
        @LLMDescription("Title of the task; must not be blank")
        title: String,
        @LLMDescription("Due date in ISO-8601 format (yyyy-MM-dd); omit if the task has no due date")
        dueDate: String? = null,
        @LLMDescription(
            "Topic to file the task under; must be one of the known topics (see listTopics). Omit to leave " +
                "the task without a topic. Never invent a topic — if the user names one that is not known, " +
                "consult listTopics and clarify with the user instead of guessing",
        )
        topic: String? = null,
    ): String =
        guarded {
            val task = service.create(CreateTaskRequest(title, dueDate.toLocalDate(), topic))
            json.encodeToString(task.toResponse())
        }

    @Tool
    @LLMDescription(
        "Update a task by id, replacing all its fields — including marking it done. " +
            "Fields left out are cleared, so pass along the values that must be kept. Fetch the task first " +
            "only when you do not already know its current values from the conversation or an earlier " +
            "tool result. Returns the updated task as JSON.",
    )
    fun updateTask(
        @LLMDescription("The id of the task to update")
        id: String,
        @LLMDescription("New title of the task; must not be blank")
        title: String,
        @LLMDescription("Due date in ISO-8601 format (yyyy-MM-dd); omit to clear it")
        dueDate: String? = null,
        @LLMDescription(
            "Topic to file the task under; must be one of the known topics (see listTopics); omit to clear it",
        )
        topic: String? = null,
        @LLMDescription("Whether the task is done")
        done: Boolean = false,
    ): String =
        guarded {
            val task = service.update(id, UpdateTaskRequest(title, dueDate.toLocalDate(), topic, done))
            json.encodeToString(task.toResponse())
        }

    private fun String?.toLocalDate(): LocalDate? =
        this?.let {
            try {
                LocalDate.parse(it)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid date '$it': use ISO-8601 format (yyyy-MM-dd)", e)
            }
        }

    private fun guarded(block: () -> String): String =
        try {
            block()
        } catch (e: TaskNotFoundException) {
            error(e.message ?: "Task not found")
        } catch (e: UnsupportedTaskOperationException) {
            error(e.message ?: "Operation not supported")
        } catch (e: VaultConflictException) {
            error(e.message ?: "Vault has conflicting edits")
        } catch (e: IllegalArgumentException) {
            error(e.message ?: "Invalid input")
        }

    private fun error(message: String): String = json.encodeToString(ToolError(message))

    @Serializable
    private data class ToolError(
        val error: String,
    )
}
