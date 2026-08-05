package ro.jf.ai.assistant.transfer

import kotlinx.serialization.Serializable
import ro.jf.ai.assistant.model.Task
import java.time.LocalDate

@Serializable
data class TaskResponse(
    val id: String,
    val title: String,
    @Serializable(with = LocalDateSerializer::class)
    val dueDate: LocalDate? = null,
    val category: String? = null,
    val completed: Boolean,
)

fun Task.toResponse() = TaskResponse(id, title, dueDate, category, completed)
