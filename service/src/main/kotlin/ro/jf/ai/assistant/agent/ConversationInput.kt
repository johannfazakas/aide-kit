package ro.jf.ai.assistant.agent

import ro.jf.ai.assistant.conversation.ConversationMessage
import ro.jf.ai.assistant.conversation.ConversationRole

fun buildAgentInput(
    history: List<ConversationMessage>,
    message: String,
): String {
    if (history.isEmpty()) return message
    val transcript =
        history.joinToString("\n") { entry ->
            val speaker =
                when (entry.role) {
                    ConversationRole.USER -> "User"
                    ConversationRole.ASSISTANT -> "Assistant"
                }
            "$speaker: ${entry.content}"
        }
    return "Conversation so far:\n$transcript\n\nUser: $message"
}
