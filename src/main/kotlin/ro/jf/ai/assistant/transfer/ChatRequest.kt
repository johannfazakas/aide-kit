package ro.jf.ai.assistant.transfer

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val message: String,
)
