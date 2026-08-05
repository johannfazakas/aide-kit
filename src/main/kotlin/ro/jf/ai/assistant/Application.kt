package ro.jf.ai.assistant

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import ro.jf.ai.assistant.agent.OPENCODE_ZEN_BASE_URL
import ro.jf.ai.assistant.agent.installAssistant
import ro.jf.ai.assistant.config.configureStatusPages
import ro.jf.ai.assistant.repository.InMemoryTaskRepository
import ro.jf.ai.assistant.repository.TaskRepository
import ro.jf.ai.assistant.routes.chatRoutes
import ro.jf.ai.assistant.routes.taskRoutes
import ro.jf.ai.assistant.service.TaskService

fun main() {
    embeddedServer(Netty, port = 8080) { module() }.start(wait = true)
}

fun Application.module(
    repository: TaskRepository = InMemoryTaskRepository(),
    openCodeApiKey: String? = System.getenv("OPENCODE_API_KEY"),
    openCodeBaseUrl: String? = System.getenv("OPENCODE_BASE_URL"),
) {
    val apiKey = openCodeApiKey?.takeIf { it.isNotBlank() }
    val baseUrl = openCodeBaseUrl?.takeIf { it.isNotBlank() } ?: OPENCODE_ZEN_BASE_URL
    install(ContentNegotiation) {
        json()
    }
    configureStatusPages()
    val service = TaskService(repository)
    if (apiKey != null) {
        installAssistant(service, apiKey, baseUrl)
    }
    routing {
        taskRoutes(service)
        chatRoutes(assistantConfigured = apiKey != null)
    }
}
