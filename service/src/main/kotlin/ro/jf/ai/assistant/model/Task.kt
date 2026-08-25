package ro.jf.ai.assistant.model

import kotlinx.datetime.LocalDate

data class Task(
    val id: String,
    val title: String,
    val dueDate: LocalDate? = null,
    val category: String? = null,
    val completed: Boolean = false,
)
