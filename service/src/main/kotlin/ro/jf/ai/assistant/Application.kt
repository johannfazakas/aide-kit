package ro.jf.ai.assistant

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import ro.jf.ai.assistant.agent.OPENCODE_ZEN_BASE_URL
import ro.jf.ai.assistant.agent.installAssistant
import ro.jf.ai.assistant.config.configureStatusPages
import ro.jf.ai.assistant.conversation.InMemoryConversationStore
import ro.jf.ai.assistant.repository.InMemoryTaskRepository
import ro.jf.ai.assistant.repository.TaskRepository
import ro.jf.ai.assistant.routes.chatRoutes
import ro.jf.ai.assistant.routes.taskRoutes
import ro.jf.ai.assistant.service.TaskService

const val DEFAULT_PORT = 7080

fun main() {
    val port =
        System.getenv("PORT")?.takeIf { it.isNotBlank() }?.let {
            requireNotNull(it.trim().toIntOrNull()) { "PORT must be an integer, got '$it'" }
        } ?: DEFAULT_PORT
    embeddedServer(Netty, port = port) { module() }.start(wait = true)
}

fun Application.module(
    repository: TaskRepository = InMemoryTaskRepository(),
    openCodeApiKey: String? = System.getenv("OPENCODE_API_KEY"),
    openCodeBaseUrl: String? = System.getenv("OPENCODE_BASE_URL"),
    corsAllowedOrigins: String? = System.getenv("CORS_ALLOWED_ORIGINS"),
) {
    val apiKey =
        requireNotNull(openCodeApiKey?.takeIf { it.isNotBlank() }) {
            "OPENCODE_API_KEY is not set; the service refuses to start without LLM configuration"
        }
    val baseUrl = openCodeBaseUrl?.takeIf { it.isNotBlank() } ?: OPENCODE_ZEN_BASE_URL
    installCors(corsAllowedOrigins)
    install(ContentNegotiation) {
        json()
    }
    configureStatusPages()
    val service = TaskService(repository)
    val conversationStore = InMemoryConversationStore()
    installAssistant(service, apiKey, baseUrl)
    routing {
        taskRoutes(service)
        chatRoutes(conversationStore)
    }
}

private val loopbackHosts = setOf("localhost", "127.0.0.1", "[::1]")

private fun String.normalizedOrigin(): String = trim().removeSuffix("/").lowercase()

private fun String.originHost(): String {
    val hostAndPort = normalizedOrigin().substringAfter("://")
    return if (hostAndPort.startsWith("[")) {
        hostAndPort.substringBefore("]") + "]"
    } else {
        hostAndPort.substringBefore(":")
    }
}

private fun Application.installCors(allowedOrigins: String?) {
    val origins =
        allowedOrigins
            ?.split(",")
            ?.map { it.normalizedOrigin() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
    install(CORS) {
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        if (origins.isEmpty()) {
            allowOrigins { it.originHost() in loopbackHosts }
        } else {
            allowOrigins { it.normalizedOrigin() in origins }
        }
    }
}
