package ro.jf.ai.assistant.agent

import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.ktor.Koog
import io.ktor.server.application.Application
import io.ktor.server.application.install
import ro.jf.ai.assistant.service.TaskService

const val OPENCODE_ZEN_BASE_URL = "https://opencode.ai/zen"

val ASSISTANT_SYSTEM_PROMPT =
    """
    You are a personal assistant helping the user manage aspects of their daily life.
    Your currently available capability is task management: use the provided tools to list, inspect,
    create, update, and complete the user's tasks.
    You cannot delete tasks; if asked to delete one, explain that deletion is not available yet.
    When the user refers to a task by its content rather than its id, list all tasks without a
    category filter and match by title; never guess a category the user did not name.
    When an instruction is ambiguous or cannot be fulfilled with your tools, ask a clarifying
    question in your reply instead of guessing.
    You do not know the current date, so you cannot resolve relative dates like "tomorrow" or
    "next Friday"; ask the user for the exact date instead of inventing one.
    The conversation has memory: earlier turns are provided to you, so you can resolve references
    like "it" or "that task" from context instead of asking the user to repeat ids.
    """.trimIndent()

fun Application.installAssistant(
    taskService: TaskService,
    apiKey: String,
    openCodeBaseUrl: String = OPENCODE_ZEN_BASE_URL,
) {
    install(Koog) {
        llm {
            openAI(apiKey) {
                baseUrl = openCodeBaseUrl
            }
        }
        agentConfig {
            prompt("assistant") {
                system(ASSISTANT_SYSTEM_PROMPT)
            }
            registerTools {
                tools(TaskTools(taskService).asTools())
            }
        }
    }
}
