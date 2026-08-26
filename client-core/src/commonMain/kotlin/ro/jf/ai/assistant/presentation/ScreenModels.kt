package ro.jf.ai.assistant.presentation

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import ro.jf.ai.assistant.client.AssistantApiClient
import ro.jf.ai.assistant.client.TasksApiClient

const val DEFAULT_SERVICE_PORT = 7080

data class ScreenModels(
    val baseUrl: String,
    val tasks: TasksScreenModel,
    val chat: ChatScreenModel,
)

fun createScreenModels(
    httpClient: HttpClient,
    baseUrl: String,
    scope: CoroutineScope,
): ScreenModels =
    ScreenModels(
        baseUrl = baseUrl,
        tasks = TasksScreenModel(TasksApiClient(httpClient, baseUrl), scope),
        chat = ChatScreenModel(AssistantApiClient(httpClient, baseUrl), scope),
    )
