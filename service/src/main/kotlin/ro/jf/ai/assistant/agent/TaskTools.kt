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
import ro.jf.ai.assistant.model.Task
import ro.jf.ai.assistant.service.TaskService
import ro.jf.ai.assistant.transfer.CreateTaskRequest
import ro.jf.ai.assistant.transfer.toResponse

@LLMDescription("Tools for managing the user's tasks")
class TaskTools(
    private val service: TaskService,
) : ToolSet {
    private val json = Json

    @Tool
    @LLMDescription(
        "List the user's tasks, optionally filtered by topic. Returns a JSON array of tasks. To find a task " +
            "described by its content, list without a topic filter and match by title.",
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
    ): String = guarded { encode(service.get(id)) }

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
            encode(service.create(CreateTaskRequest(title, dueDate.toLocalDate(), topic)))
        }

    @Tool
    @LLMDescription(
        "Mark a task as done by id. Every other field stays unchanged. Returns the updated task as JSON.",
    )
    fun completeTask(
        @LLMDescription("The id of the task to complete")
        id: String,
    ): String = guarded { encode(service.complete(id)) }

    @Tool
    @LLMDescription(
        "Mark a done task as not done again by id. Every other field stays unchanged. " +
            "Returns the updated task as JSON.",
    )
    fun reopenTask(
        @LLMDescription("The id of the task to reopen")
        id: String,
    ): String = guarded { encode(service.reopen(id)) }

    @Tool
    @LLMDescription(
        "Set, change, or clear a task's due date by id. Every other field stays unchanged. " +
            "Returns the updated task as JSON.",
    )
    fun rescheduleTask(
        @LLMDescription("The id of the task to reschedule")
        id: String,
        @LLMDescription("New due date in ISO-8601 format (yyyy-MM-dd); omit to clear the due date")
        dueDate: String? = null,
    ): String = guarded { encode(service.reschedule(id, dueDate.toLocalDate())) }

    @Tool
    @LLMDescription(
        "Change a task's title by id. Every other field stays unchanged. Returns the updated task as JSON.",
    )
    fun renameTask(
        @LLMDescription("The id of the task to rename")
        id: String,
        @LLMDescription("New title of the task; must not be blank")
        title: String,
    ): String = guarded { encode(service.rename(id, title)) }

    @Tool
    @LLMDescription(
        "Move a task to another topic by id, or remove its topic. Every other field stays unchanged. " +
            "The topic must be one of the known topics (see listTopics); never invent one — if the user " +
            "names an unknown topic, consult listTopics and clarify with the user instead of guessing. " +
            "Returns the updated task as JSON.",
    )
    fun changeTaskTopic(
        @LLMDescription("The id of the task to move")
        id: String,
        @LLMDescription("Topic to move the task to; omit to remove the topic (the task goes to the inbox)")
        topic: String? = null,
    ): String = guarded { encode(service.changeTopic(id, topic)) }

    private fun encode(task: Task): String = json.encodeToString(task.toResponse())

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
