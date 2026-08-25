package ro.jf.ai.assistant.conversation

enum class ConversationRole { USER, ASSISTANT }

data class ConversationMessage(
    val role: ConversationRole,
    val content: String,
)
