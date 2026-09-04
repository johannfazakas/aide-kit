package ro.jf.ai.assistant.model

import kotlinx.datetime.LocalDate

data class Task(
    val id: String,
    val title: String,
    val dueDate: LocalDate? = null,
    val topic: String? = null,
    val done: Boolean = false,
)
