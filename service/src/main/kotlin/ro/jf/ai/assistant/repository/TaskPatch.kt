package ro.jf.ai.assistant.repository

import kotlinx.datetime.LocalDate
import ro.jf.ai.assistant.model.Task

@JvmInline
value class Change<out T>(
    val value: T,
)

class TaskPatch(
    val title: Change<String>? = null,
    val dueDate: Change<LocalDate?>? = null,
    val topic: Change<String?>? = null,
    val done: Change<Boolean>? = null,
) {
    fun applyTo(task: Task): Task =
        task.copy(
            title = title?.value ?: task.title,
            dueDate = if (dueDate != null) dueDate.value else task.dueDate,
            topic = if (topic != null) topic.value else task.topic,
            done = done?.value ?: task.done,
        )

    companion object {
        fun complete() = TaskPatch(done = Change(true))

        fun reopen() = TaskPatch(done = Change(false))

        fun reschedule(dueDate: LocalDate?) = TaskPatch(dueDate = Change(dueDate))

        fun rename(title: String) = TaskPatch(title = Change(title))

        fun changeTopic(topic: String?) = TaskPatch(topic = Change(topic))

        fun replace(
            title: String,
            dueDate: LocalDate?,
            topic: String?,
            done: Boolean,
        ) = TaskPatch(
            title = Change(title),
            dueDate = Change(dueDate),
            topic = Change(topic),
            done = Change(done),
        )
    }
}
