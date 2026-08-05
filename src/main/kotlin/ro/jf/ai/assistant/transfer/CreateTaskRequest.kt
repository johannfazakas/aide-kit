package ro.jf.ai.assistant.transfer

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class CreateTaskRequest(
    val title: String,
    @Serializable(with = LocalDateSerializer::class)
    val dueDate: LocalDate? = null,
    val category: String? = null,
    val completed: Boolean = false,
)
