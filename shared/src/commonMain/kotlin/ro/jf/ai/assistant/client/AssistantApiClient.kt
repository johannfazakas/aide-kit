package ro.jf.ai.assistant.client

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import ro.jf.ai.assistant.transfer.ChatRequest
import ro.jf.ai.assistant.transfer.ChatResponse

class AssistantApiClient(
    httpClient: HttpClient,
    private val baseUrl: String,
) {
    private val client = httpClient.configuredForApi()

    suspend fun chat(request: ChatRequest): ChatResponse =
        client
            .post("$baseUrl/api/v1/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.bodyOrThrow()
}
