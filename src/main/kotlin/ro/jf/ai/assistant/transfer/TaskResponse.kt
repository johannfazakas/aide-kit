package ro.jf.ai.assistant.transfer

import java.time.LocalDate
import kotlinx.serialization.Serializable
import ro.jf.ai.assistant.model.Task

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
