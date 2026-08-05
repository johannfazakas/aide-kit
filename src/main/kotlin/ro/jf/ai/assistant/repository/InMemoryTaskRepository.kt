package ro.jf.ai.assistant.repository

import ro.jf.ai.assistant.model.Task
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryTaskRepository(
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : TaskRepository {
    private val tasks = ConcurrentHashMap<String, Task>()

    override fun create(
        title: String,
        dueDate: LocalDate?,
        category: String?,
        completed: Boolean,
    ): Task {
        val task = Task(idGenerator(), title, dueDate, category, completed)
        tasks[task.id] = task
        return task
    }

    override fun findAll(category: String?): List<Task> =
        tasks.values.filter { category == null || it.category == category }

    override fun findById(id: String): Task? = tasks[id]

    override fun update(
        id: String,
        title: String,
        dueDate: LocalDate?,
        category: String?,
        completed: Boolean,
    ): Task? = tasks.computeIfPresent(id) { _, _ -> Task(id, title, dueDate, category, completed) }

    override fun delete(id: String): Boolean = tasks.remove(id) != null
}
