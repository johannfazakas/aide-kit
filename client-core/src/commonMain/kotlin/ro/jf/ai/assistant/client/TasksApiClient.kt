package ro.jf.ai.assistant.client

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import ro.jf.ai.assistant.transfer.CreateTaskRequest
import ro.jf.ai.assistant.transfer.TaskResponse
import ro.jf.ai.assistant.transfer.UpdateTaskRequest

class TasksApiClient(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun listTasks(category: String? = null): List<TaskResponse> =
        client
            .get("$baseUrl/api/v1/tasks") {
                if (category != null) parameter("category", category)
            }.bodyOrThrow()

    suspend fun getTask(id: String): TaskResponse = client.get("$baseUrl/api/v1/tasks/$id").bodyOrThrow()

    suspend fun createTask(request: CreateTaskRequest): TaskResponse =
        client
            .post("$baseUrl/api/v1/tasks") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.bodyOrThrow()

    suspend fun updateTask(
        id: String,
        request: UpdateTaskRequest,
    ): TaskResponse =
        client
            .put("$baseUrl/api/v1/tasks/$id") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.bodyOrThrow()

    suspend fun deleteTask(id: String) {
        client.delete("$baseUrl/api/v1/tasks/$id").bodyOrThrow<Unit>()
    }
}
