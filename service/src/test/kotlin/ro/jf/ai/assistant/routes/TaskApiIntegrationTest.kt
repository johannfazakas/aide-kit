package ro.jf.ai.assistant.routes

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import ro.jf.ai.assistant.module
import ro.jf.ai.assistant.transfer.CreateTaskRequest
import ro.jf.ai.assistant.transfer.ErrorResponse
import ro.jf.ai.assistant.transfer.TaskResponse
import ro.jf.ai.assistant.transfer.UpdateTaskRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskApiIntegrationTest {
    private fun apiTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { module(openCodeApiKey = "test-key") }
            val client =
                createClient {
                    install(ContentNegotiation) { json() }
                }
            block(client)
        }

    @Test
    fun `given a started application when listing tasks then responds 200 with empty array`() =
        apiTest { client ->
            val response = client.get("/api/v1/tasks")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("[]", response.bodyAsText())
        }

    @Test
    fun `given a task lifecycle when create get list update delete then behaves per spec`() =
        apiTest { client ->
            val createResponse =
                client.post("/api/v1/tasks") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateTaskRequest(
                            title = "Pay rent",
                            dueDate = LocalDate.parse("2026-07-31"),
                            category = "home",
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, createResponse.status)
            val created = createResponse.body<TaskResponse>()
            assertTrue(created.id.isNotBlank())
            assertEquals("Pay rent", created.title)
            assertEquals(LocalDate.parse("2026-07-31"), created.dueDate)
            assertEquals("home", created.category)
            assertFalse(created.completed)

            val fetched = client.get("/api/v1/tasks/${created.id}").body<TaskResponse>()
            assertEquals(created, fetched)

            client.post("/api/v1/tasks") {
                contentType(ContentType.Application.Json)
                setBody(CreateTaskRequest(title = "Report", category = "work"))
            }
            val all = client.get("/api/v1/tasks").body<List<TaskResponse>>()
            assertEquals(2, all.size)
            val filtered = client.get("/api/v1/tasks?category=home").body<List<TaskResponse>>()
            assertEquals(listOf(created), filtered)

            val updateResponse =
                client.put("/api/v1/tasks/${created.id}") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateTaskRequest(title = "Pay rent", completed = true))
                }
            assertEquals(HttpStatusCode.OK, updateResponse.status)
            val updated = updateResponse.body<TaskResponse>()
            assertEquals(created.id, updated.id)
            assertTrue(updated.completed)
            assertNull(updated.dueDate)
            assertNull(updated.category)

            val deleteResponse = client.delete("/api/v1/tasks/${created.id}")
            assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
            assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/tasks/${created.id}").status)
        }

    @Test
    fun `given a blank title when creating then responds 400 with message`() =
        apiTest { client ->
            val response =
                client.post("/api/v1/tasks") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTaskRequest(title = "  "))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.body<ErrorResponse>().message.isNotBlank())
        }

    @Test
    fun `given a client-supplied id when creating then responds 400 with message`() =
        apiTest { client ->
            val response =
                client.post("/api/v1/tasks") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"id": "my-custom-id", "title": "Sneaky"}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.body<ErrorResponse>().message.isNotBlank())
        }

    @Test
    fun `given malformed json when creating then responds 400 with message`() =
        apiTest { client ->
            val response =
                client.post("/api/v1/tasks") {
                    contentType(ContentType.Application.Json)
                    setBody("{not json")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.body<ErrorResponse>().message.isNotBlank())
        }

    @Test
    fun `given an invalid due date when creating then responds 400 with message`() =
        apiTest { client ->
            val response =
                client.post("/api/v1/tasks") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title": "Task", "dueDate": "next tuesday"}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.body<ErrorResponse>().message.isNotBlank())
        }

    @Test
    fun `given an unknown id when get update delete then responds 404 with message`() =
        apiTest { client ->
            val getResponse = client.get("/api/v1/tasks/missing")
            assertEquals(HttpStatusCode.NotFound, getResponse.status)
            assertTrue(getResponse.body<ErrorResponse>().message.isNotBlank())

            val putResponse =
                client.put("/api/v1/tasks/missing") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateTaskRequest(title = "Title"))
                }
            assertEquals(HttpStatusCode.NotFound, putResponse.status)

            val deleteResponse = client.delete("/api/v1/tasks/missing")
            assertEquals(HttpStatusCode.NotFound, deleteResponse.status)
        }
}
