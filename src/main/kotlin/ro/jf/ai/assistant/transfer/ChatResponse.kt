package ro.jf.ai.assistant.transfer

import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val sessionId: String,
    val reply: String,
)
