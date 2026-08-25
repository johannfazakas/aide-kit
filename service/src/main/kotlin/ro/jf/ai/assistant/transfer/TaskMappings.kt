package ro.jf.ai.assistant.transfer

import ro.jf.ai.assistant.model.Task

fun Task.toResponse() = TaskResponse(id, title, dueDate, category, completed)
