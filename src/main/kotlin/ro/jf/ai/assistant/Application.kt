package ro.jf.ai.assistant

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import ro.jf.ai.assistant.exception.TaskNotFoundException
import ro.jf.ai.assistant.repository.InMemoryTaskRepository
import ro.jf.ai.assistant.repository.TaskRepository
import ro.jf.ai.assistant.routes.taskRoutes
import ro.jf.ai.assistant.service.TaskService
import ro.jf.ai.assistant.transfer.ErrorResponse

fun main() {
    embeddedServer(Netty, port = 8080) { module() }.start(wait = true)
}

fun Application.module(repository: TaskRepository = InMemoryTaskRepository()) {
    install(ContentNegotiation) {
        json()
    }
    install(StatusPages) {
        exception<TaskNotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "Task not found"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Invalid request"))
        }
        exception<BadRequestException> { call, cause ->
            val reason = generateSequence(cause as Throwable) { it.cause }.last().message
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(reason ?: "Invalid request body"))
        }
    }
    val service = TaskService(repository)
    routing {
        taskRoutes(service)
    }
}
