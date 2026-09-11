package ro.jf.ai.assistant.service

import kotlinx.datetime.LocalDate
import ro.jf.ai.assistant.exception.TaskNotFoundException
import ro.jf.ai.assistant.model.Task
import ro.jf.ai.assistant.repository.TaskPatch
import ro.jf.ai.assistant.repository.TaskRepository
import ro.jf.ai.assistant.transfer.CreateTaskRequest
import ro.jf.ai.assistant.transfer.UpdateTaskRequest

class TaskService(
    private val repository: TaskRepository,
) {
    fun create(request: CreateTaskRequest): Task {
        validateTitle(request.title)
        return repository.create(request.title, request.dueDate, request.topic, request.done)
    }

    fun list(topic: String? = null): List<Task> = repository.findAll(topic)

    fun listTopics(): List<String> = repository.listTopics()

    fun get(id: String): Task = repository.findById(id) ?: throw TaskNotFoundException(id)

    fun update(
        id: String,
        request: UpdateTaskRequest,
    ): Task {
        validateTitle(request.title)
        return repository.update(
            id,
            TaskPatch.replace(request.title, request.dueDate, request.topic, request.done),
        ) ?: throw TaskNotFoundException(id)
    }

    fun complete(id: String): Task = apply(id, TaskPatch.complete())

    fun reopen(id: String): Task = apply(id, TaskPatch.reopen())

    fun reschedule(
        id: String,
        dueDate: LocalDate?,
    ): Task = apply(id, TaskPatch.reschedule(dueDate))

    fun rename(
        id: String,
        title: String,
    ): Task {
        validateTitle(title)
        return apply(id, TaskPatch.rename(title))
    }

    fun changeTopic(
        id: String,
        topic: String?,
    ): Task = apply(id, TaskPatch.changeTopic(topic))

    fun delete(id: String) {
        if (!repository.delete(id)) throw TaskNotFoundException(id)
    }

    private fun apply(
        id: String,
        patch: TaskPatch,
    ): Task = repository.update(id, patch) ?: throw TaskNotFoundException(id)

    private fun validateTitle(title: String) {
        require(title.isNotBlank()) { "Title must not be blank" }
        require(title.none { it.isISOControl() }) {
            "Title must be a single line without line breaks or control characters"
        }
    }
}
