package ro.jf.ai.assistant.service

import ro.jf.ai.assistant.exception.TaskNotFoundException
import ro.jf.ai.assistant.model.Task
import ro.jf.ai.assistant.repository.TaskRepository
import ro.jf.ai.assistant.transfer.CreateTaskRequest
import ro.jf.ai.assistant.transfer.UpdateTaskRequest

class TaskService(
    private val repository: TaskRepository,
) {
    fun create(request: CreateTaskRequest): Task {
        require(request.title.isNotBlank()) { "Title must not be blank" }
        return repository.create(request.title, request.dueDate, request.category, request.completed)
    }

    fun list(category: String? = null): List<Task> = repository.findAll(category)

    fun get(id: String): Task = repository.findById(id) ?: throw TaskNotFoundException(id)

    fun update(
        id: String,
        request: UpdateTaskRequest,
    ): Task {
        require(request.title.isNotBlank()) { "Title must not be blank" }
        return repository.update(id, request.title, request.dueDate, request.category, request.completed)
            ?: throw TaskNotFoundException(id)
    }

    fun delete(id: String) {
        if (!repository.delete(id)) throw TaskNotFoundException(id)
    }
}
