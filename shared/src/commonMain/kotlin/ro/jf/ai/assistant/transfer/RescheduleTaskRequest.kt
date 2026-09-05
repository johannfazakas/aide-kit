package ro.jf.ai.assistant.transfer

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class RescheduleTaskRequest(
    val dueDate: LocalDate? = null,
)
