package ro.jf.ai.assistant.routes

import ai.koog.ktor.aiAgent
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import ro.jf.ai.assistant.agent.AssistantModel
import ro.jf.ai.assistant.transfer.ChatRequest
import ro.jf.ai.assistant.transfer.ChatResponse
import ro.jf.ai.assistant.transfer.ErrorResponse

fun Route.chatRoutes(assistantConfigured: Boolean) {
    route("/api/v1/chat") {
        post {
            val request = call.receive<ChatRequest>()
            require(request.message.isNotBlank()) { "Message must not be blank" }
            if (!assistantConfigured) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("Assistant is not configured; set the OPENCODE_API_KEY environment variable"),
                )
            } else {
                val reply = aiAgent(request.message, model = AssistantModel.GLM_5_2)
                call.respond(ChatResponse(reply))
            }
        }
    }
}
