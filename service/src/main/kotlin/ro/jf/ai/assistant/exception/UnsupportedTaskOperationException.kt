package ro.jf.ai.assistant.exception

class UnsupportedTaskOperationException(
    operation: String,
) : RuntimeException("$operation is not yet supported for Obsidian storage")
