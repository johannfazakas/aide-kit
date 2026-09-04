package ro.jf.ai.assistant.repository

import kotlinx.datetime.LocalDate
import ro.jf.ai.assistant.model.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

val DEFAULT_TOPICS = listOf("home", "work", "health", "family", "finance")

class InMemoryTaskRepository(
    private val topics: List<String> = DEFAULT_TOPICS,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : TaskRepository {
    private val tasks = ConcurrentHashMap<String, Task>()

    override fun create(
        title: String,
        dueDate: LocalDate?,
        topic: String?,
        done: Boolean,
    ): Task {
        val task = Task(idGenerator(), title, dueDate, topic, done)
        tasks[task.id] = task
        return task
    }

    override fun findAll(topic: String?): List<Task> = tasks.values.filter { topic == null || it.topic == topic }

    override fun findById(id: String): Task? = tasks[id]

    override fun update(
        id: String,
        title: String,
        dueDate: LocalDate?,
        topic: String?,
        done: Boolean,
    ): Task? = tasks.computeIfPresent(id) { _, _ -> Task(id, title, dueDate, topic, done) }

    override fun delete(id: String): Boolean = tasks.remove(id) != null

    override fun listTopics(): List<String> = topics
}
