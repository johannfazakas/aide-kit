package ro.jf.ai.assistant.repository

import ro.jf.ai.assistant.model.Task
import java.time.LocalDate

interface TaskRepository {
    fun create(title: String, dueDate: LocalDate?, category: String?, completed: Boolean): Task
    fun findAll(category: String? = null): List<Task>
    fun findById(id: String): Task?
    fun update(id: String, title: String, dueDate: LocalDate?, category: String?, completed: Boolean): Task?
    fun delete(id: String): Boolean
}
