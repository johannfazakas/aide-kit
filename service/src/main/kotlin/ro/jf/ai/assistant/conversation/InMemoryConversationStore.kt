package ro.jf.ai.assistant.conversation

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

const val DEFAULT_MAX_HISTORY_MESSAGES = 20

class InMemoryConversationStore(
    private val maxMessages: Int = DEFAULT_MAX_HISTORY_MESSAGES,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val conversations = ConcurrentHashMap<String, List<ConversationMessage>>()

    fun resolveSessionId(requestedId: String?): String =
        requestedId?.takeIf(conversations::containsKey) ?: idGenerator()

    fun history(sessionId: String): List<ConversationMessage> = conversations[sessionId] ?: emptyList()

    fun append(
        sessionId: String,
        vararg messages: ConversationMessage,
    ) {
        conversations.compute(sessionId) { _, existing ->
            ((existing ?: emptyList()) + messages).takeLast(maxMessages)
        }
    }
}
