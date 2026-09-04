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
        validateTopic(request.topic)
        return repository.create(request.title, request.dueDate, request.topic, request.done)
    }

    fun list(topic: String? = null): List<Task> = repository.findAll(topic)

    fun listTopics(): List<String> = repository.listTopics()

    fun get(id: String): Task = repository.findById(id) ?: throw TaskNotFoundException(id)

    fun update(
        id: String,
        request: UpdateTaskRequest,
    ): Task {
        require(request.title.isNotBlank()) { "Title must not be blank" }
        validateTopic(request.topic)
        return repository.update(id, request.title, request.dueDate, request.topic, request.done)
            ?: throw TaskNotFoundException(id)
    }

    fun delete(id: String) {
        if (!repository.delete(id)) throw TaskNotFoundException(id)
    }

    private fun validateTopic(topic: String?) {
        if (topic == null) return
        val topics = repository.listTopics()
        if (topic !in topics) {
            throw IllegalArgumentException(
                "Unknown topic '$topic'; choose one of $topics or omit the topic",
            )
        }
    }
}
