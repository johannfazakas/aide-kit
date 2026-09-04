package ro.jf.ai.assistant.transfer

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class TaskResponse(
    val id: String,
    val title: String,
    val dueDate: LocalDate? = null,
    val topic: String? = null,
    val done: Boolean,
)
