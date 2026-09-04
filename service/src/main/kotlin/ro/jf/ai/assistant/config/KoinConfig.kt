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
import ro.jf.ai.assistant.repository.obsidian.ObsidianConfig
import ro.jf.ai.assistant.repository.obsidian.ObsidianTaskRepository
import ro.jf.ai.assistant.repository.obsidian.VaultGitBridge
import ro.jf.ai.assistant.repository.obsidian.VaultScanner
import ro.jf.ai.assistant.service.TaskService
import java.io.File

const val DEFAULT_CLONE_DIR = "vault-clone"

fun Application.configureKoin(koinModules: List<Module>) {
    install(Koin) {
        slf4jLogger()
        modules(koinModules)
    }
}

fun serviceModule(config: StartupConfig = StartupConfig()): Module {
    val taskRepository = buildTaskRepository(config)
    return module {
        single<TaskRepository> { taskRepository }
        single { TaskService(get()) }
        single { InMemoryConversationStore() }
    }
}

private fun buildTaskRepository(config: StartupConfig): TaskRepository =
    when (
        val storage =
            config.taskStorage
                ?.trim()
                ?.lowercase()
                ?.ifBlank { null } ?: "memory"
    ) {
        "memory" -> {
            InMemoryTaskRepository()
        }

        "obsidian" -> {
            val repoUrl =
                requireNotNull(config.obsidianRepoUrl?.takeIf { it.isNotBlank() }) {
                    "OBSIDIAN_REPO_URL is not set; it is required when TASK_STORAGE=obsidian"
                }
            val repoToken =
                requireNotNull(config.obsidianRepoToken?.takeIf { it.isNotBlank() }) {
                    "OBSIDIAN_REPO_TOKEN is not set; it is required when TASK_STORAGE=obsidian"
                }
            val obsidianConfig =
                ObsidianConfig(
                    repoUrl = repoUrl,
                    token = repoToken,
                    branch = config.obsidianRepoBranch?.takeIf { it.isNotBlank() } ?: "main",
                    cloneDir = File(config.obsidianCloneDir?.takeIf { it.isNotBlank() } ?: DEFAULT_CLONE_DIR),
                )
            val bridge = VaultGitBridge(obsidianConfig)
            ObsidianTaskRepository(
                bridge = bridge,
                scanner =
                    VaultScanner(
                        inboxPath = obsidianConfig.inboxPath,
                        registryPath = obsidianConfig.registryPath,
                    ),
                inboxPath = obsidianConfig.inboxPath,
            )
        }

        else -> {
            throw IllegalArgumentException(
                "TASK_STORAGE must be 'memory' or 'obsidian', got '$storage'",
            )
        }
    }
