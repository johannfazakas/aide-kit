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
    private val lock = Any()

    override fun create(
        title: String,
        dueDate: LocalDate?,
        topic: String?,
        done: Boolean,
    ): Task {
        requireKnownTopic(topic)
        val task = Task(idGenerator(), title, dueDate, topic, done)
        tasks[task.id] = task
        return task
    }

    override fun findAll(topic: String?): List<Task> = tasks.values.filter { topic == null || it.topic == topic }

    override fun findById(id: String): Task? = tasks[id]

    override fun update(
        id: String,
        patch: TaskPatch,
    ): Task? =
        synchronized(lock) {
            val current = tasks[id] ?: return null
            requireKnownTopic(patch.topic?.value)
            val updated = patch.applyTo(current)
            tasks[id] = updated
            updated
        }

    override fun delete(id: String): Boolean = tasks.remove(id) != null

    override fun listTopics(): List<String> = topics

    private fun requireKnownTopic(topic: String?) {
        if (topic != null && topic !in topics) {
            throw IllegalArgumentException("Unknown topic '$topic'; choose one of $topics or omit the topic")
        }
    }
}
