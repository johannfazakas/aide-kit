package ro.jf.ai.assistant.client

import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ro.jf.ai.assistant.transfer.CreateTaskRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TasksApiClientTest {
    private fun tasksApiClient(handler: MockRequestHandler) = TasksApiClient(testApiHttpClient(handler), "http://test")

    @Test
    fun `given a create request when posted then the request matches the rest contract and the task deserializes`() =
        runTest {
            val client =
                tasksApiClient { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("http://test/api/v1/tasks", request.url.toString())
                    assertEquals(
                        """{"title":"Dentist","dueDate":"2026-09-01","topic":"health","done":false}""",
                        (request.body as TextContent).text,
                    )
                    respond(
                        """{"id":"t1","title":"Dentist","dueDate":"2026-09-01","topic":"health","done":false}""",
                        HttpStatusCode.Created,
                        jsonHeaders(),
                    )
                }

            val task =
                client.createTask(
                    CreateTaskRequest(title = "Dentist", dueDate = LocalDate.parse("2026-09-01"), topic = "health"),
                )

            assertEquals("t1", task.id)
            assertEquals(LocalDate.parse("2026-09-01"), task.dueDate)
        }

    @Test
    fun `given a topic filter when listing tasks then the query parameter is sent`() =
        runTest {
            val client =
                tasksApiClient { request ->
                    assertEquals("http://test/api/v1/tasks?topic=home", request.url.toString())
                    respond("[]", HttpStatusCode.OK, jsonHeaders())
                }

            assertEquals(emptyList(), client.listTasks(topic = "home"))
        }

    @Test
    fun `given a topics response when listing topics then the names are returned`() =
        runTest {
            val client =
                tasksApiClient { request ->
                    assertEquals("http://test/api/v1/topics", request.url.toString())
                    respond("""["home","work"]""", HttpStatusCode.OK, jsonHeaders())
                }

            assertEquals(listOf("home", "work"), client.listTopics())
        }

    @Test
    fun `given a delete call when the server responds no content then it completes without error`() =
        runTest {
            val client =
                tasksApiClient { request ->
                    assertEquals(HttpMethod.Delete, request.method)
                    assertEquals("http://test/api/v1/tasks/t1", request.url.toString())
                    respond("", HttpStatusCode.NoContent)
                }

            client.deleteTask("t1")
        }

    @Test
    fun `given a server error with an error body when calling then the status and message are surfaced`() =
        runTest {
            val client =
                tasksApiClient {
                    respond("""{"message":"Task with id t1 not found"}""", HttpStatusCode.NotFound, jsonHeaders())
                }

            val exception = assertFailsWith<ApiException> { client.getTask("t1") }

            assertEquals(404, exception.status)
            assertEquals("Task with id t1 not found", exception.message)
        }
}
