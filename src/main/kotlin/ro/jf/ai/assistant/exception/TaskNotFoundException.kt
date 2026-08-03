package ro.jf.ai.assistant.exception

class TaskNotFoundException(id: String) : RuntimeException("Task not found: $id")
