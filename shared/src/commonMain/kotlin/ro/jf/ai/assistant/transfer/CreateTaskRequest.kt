package ro.jf.ai.assistant.transfer

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class CreateTaskRequest(
    val title: String,
    val dueDate: LocalDate? = null,
    val category: String? = null,
    val completed: Boolean = false,
)
