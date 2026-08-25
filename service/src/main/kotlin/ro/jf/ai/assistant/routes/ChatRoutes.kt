package ro.jf.ai.assistant.routes

import ai.koog.ktor.aiAgent
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import ro.jf.ai.assistant.agent.AssistantModel
import ro.jf.ai.assistant.agent.assistantStrategy
import ro.jf.ai.assistant.agent.buildAgentInput
import ro.jf.ai.assistant.conversation.ConversationMessage
import ro.jf.ai.assistant.conversation.ConversationRole
import ro.jf.ai.assistant.conversation.InMemoryConversationStore
import ro.jf.ai.assistant.transfer.ChatRequest
import ro.jf.ai.assistant.transfer.ChatResponse

fun Route.chatRoutes(conversationStore: InMemoryConversationStore) {
    route("/api/v1/chat") {
        post {
            val request = call.receive<ChatRequest>()
            require(request.message.isNotBlank()) { "Message must not be blank" }
            val sessionId = conversationStore.resolveSessionId(request.sessionId)
            val input = buildAgentInput(conversationStore.history(sessionId), request.message)
            val reply = aiAgent(assistantStrategy(), AssistantModel.GLM_5_2, input)
            conversationStore.append(
                sessionId,
                ConversationMessage(ConversationRole.USER, request.message),
                ConversationMessage(ConversationRole.ASSISTANT, reply),
            )
            call.respond(ChatResponse(sessionId, reply))
        }
    }
}
