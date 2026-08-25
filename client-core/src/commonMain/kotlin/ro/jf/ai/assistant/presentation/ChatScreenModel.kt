package ro.jf.ai.assistant.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.jf.ai.assistant.client.AssistantApiClient
import ro.jf.ai.assistant.transfer.ChatRequest

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
    val role: ChatRole,
    val content: String,
)

data class ChatState(
    val transcript: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
)

class ChatScreenModel(
    private val client: AssistantApiClient,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = mutableState.asStateFlow()

    private var sessionId: String? = null

    fun send(message: String) {
        if (message.isBlank() || mutableState.value.sending) return
        mutableState.update {
            it.copy(
                transcript = it.transcript + ChatMessage(ChatRole.USER, message),
                sending = true,
                error = null,
            )
        }
        scope.launch {
            runCatching { client.chat(ChatRequest(message = message, sessionId = sessionId)) }
                .onSuccess { response ->
                    sessionId = response.sessionId
                    mutableState.update {
                        it.copy(
                            transcript = it.transcript + ChatMessage(ChatRole.ASSISTANT, response.reply),
                            sending = false,
                        )
                    }
                }.onFailure { cause ->
                    mutableState.update { it.copy(sending = false, error = cause.toUserMessage()) }
                }
        }
    }
}
