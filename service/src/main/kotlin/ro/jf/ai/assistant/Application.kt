package ro.jf.ai.assistant

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import org.koin.core.module.Module
import org.koin.ktor.ext.get
import ro.jf.ai.assistant.agent.OPENCODE_ZEN_BASE_URL
import ro.jf.ai.assistant.agent.installAssistant
import ro.jf.ai.assistant.config.StartupConfig
import ro.jf.ai.assistant.config.configureCors
import ro.jf.ai.assistant.config.configureKoin
import ro.jf.ai.assistant.config.configureStatusPages
import ro.jf.ai.assistant.config.loadStartupConfig
import ro.jf.ai.assistant.config.serviceModule
import ro.jf.ai.assistant.conversation.InMemoryConversationStore
import ro.jf.ai.assistant.routes.chatRoutes
import ro.jf.ai.assistant.routes.taskRoutes
import ro.jf.ai.assistant.service.TaskService

const val DEFAULT_PORT = 7080

fun main() {
    val startup = loadStartupConfig()
    val port =
        startup.port?.let {
            requireNotNull(it.toIntOrNull()) { "PORT must be an integer, got '$it'" }
        } ?: DEFAULT_PORT
    embeddedServer(Netty, port = port) {
        module(startup)
    }.start(wait = true)
}

fun Application.module(
    config: StartupConfig = StartupConfig(),
    koinModules: List<Module>? = null,
) {
    val apiKey =
        requireNotNull(config.openCodeApiKey?.takeIf { it.isNotBlank() }) {
            "OPENCODE_API_KEY is not set; the service refuses to start without LLM configuration"
        }
    val baseUrl = config.openCodeBaseUrl?.takeIf { it.isNotBlank() } ?: OPENCODE_ZEN_BASE_URL
    val modules = koinModules ?: listOf(serviceModule(config))
    configureKoin(modules)
    configureCors(config.corsAllowedOrigins)
    install(ContentNegotiation) {
        json()
    }
    configureStatusPages()
    val service = get<TaskService>()
    installAssistant(service, apiKey, baseUrl)
    routing {
        taskRoutes(service)
        chatRoutes(get<InMemoryConversationStore>())
    }
}
