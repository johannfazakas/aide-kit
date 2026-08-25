package ro.jf.ai.assistant.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ro.jf.ai.assistant.exception.TaskNotFoundException
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
        "List the user's tasks, optionally filtered by category. Returns a JSON array of tasks. " +
            "To find a task described by its content, list without a category filter and match by title.",
    )
    fun listTasks(
        @LLMDescription(
            "Category to filter by; omit to list all tasks. Only pass a category the user explicitly named — " +
                "it is a user-defined label such as 'home' or 'work', never guessed from task content",
        )
        category: String? = null,
    ): String =
        guarded {
            json.encodeToString(service.list(category).map { it.toResponse() })
        }

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
        @LLMDescription("Category of the task; omit if uncategorized")
        category: String? = null,
    ): String =
        guarded {
            val task = service.create(CreateTaskRequest(title, dueDate.toLocalDate(), category))
            json.encodeToString(task.toResponse())
        }

    @Tool
    @LLMDescription(
        "Update a task by id, replacing all its fields — including marking it completed. " +
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
        @LLMDescription("Category of the task; omit to clear it")
        category: String? = null,
        @LLMDescription("Whether the task is completed")
        completed: Boolean = false,
    ): String =
        guarded {
            val task = service.update(id, UpdateTaskRequest(title, dueDate.toLocalDate(), category, completed))
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
        } catch (e: IllegalArgumentException) {
            error(e.message ?: "Invalid input")
        }

    private fun error(message: String): String = json.encodeToString(ToolError(message))

    @Serializable
    private data class ToolError(
        val error: String,
    )
}
