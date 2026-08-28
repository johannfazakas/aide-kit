package ro.jf.ai.assistant.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import ro.jf.ai.assistant.conversation.InMemoryConversationStore
import ro.jf.ai.assistant.repository.InMemoryTaskRepository
import ro.jf.ai.assistant.repository.TaskRepository
import ro.jf.ai.assistant.service.TaskService

fun Application.configureKoin(koinModules: List<Module>) {
    install(Koin) {
        slf4jLogger()
        modules(koinModules)
    }
}

val serviceModule =
    module {
        single<TaskRepository> { InMemoryTaskRepository() }
        single { TaskService(get()) }
        single { InMemoryConversationStore() }
    }
