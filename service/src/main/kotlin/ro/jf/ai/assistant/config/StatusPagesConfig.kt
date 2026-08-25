package ro.jf.ai.assistant.config

import ai.koog.agents.core.agent.exception.AIAgentException
import ai.koog.prompt.executor.clients.LLMClientException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import ro.jf.ai.assistant.exception.TaskNotFoundException
import ro.jf.ai.assistant.transfer.ErrorResponse

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<TaskNotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "Task not found"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Invalid request"))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.rootMessage("Invalid request body")))
        }
        exception<AIAgentException> { call, cause ->
            call.respond(HttpStatusCode.BadGateway, ErrorResponse(cause.rootMessage("Assistant run failed")))
        }
        exception<LLMClientException> { call, cause ->
            call.respond(HttpStatusCode.BadGateway, ErrorResponse(cause.rootMessage("LLM request failed")))
        }
    }
}

private fun Throwable.rootMessage(fallback: String): String =
    generateSequence(this) { it.cause }.mapNotNull { it.message }.lastOrNull() ?: fallback
