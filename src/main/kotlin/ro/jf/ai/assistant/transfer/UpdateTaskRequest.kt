package ro.jf.ai.assistant.transfer

import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class UpdateTaskRequest(
    val title: String,
    @Serializable(with = LocalDateSerializer::class)
    val dueDate: LocalDate? = null,
    val category: String? = null,
    val completed: Boolean = false,
)
