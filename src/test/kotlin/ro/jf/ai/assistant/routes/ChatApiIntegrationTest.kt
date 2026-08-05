package ro.jf.ai.assistant.routes

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import ai.koog.agents.core.agent.exception.AIAgentException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import ro.jf.ai.assistant.module
import ro.jf.ai.assistant.transfer.ChatRequest
import ro.jf.ai.assistant.transfer.CreateTaskRequest
import ro.jf.ai.assistant.transfer.ErrorResponse
import ro.jf.ai.assistant.transfer.TaskResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatApiIntegrationTest {

    private fun chatApiTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application { module(openCodeApiKey = null) }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }
        block(client)
    }

    @Test
    fun `given no api key when posting a chat message then responds 503 with error`() = chatApiTest { client ->
        val response = client.post("/api/v1/chat") {
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(message = "What tasks do I have?"))
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.body<ErrorResponse>().message.contains("OPENCODE_API_KEY"))
    }

    @Test
    fun `given a blank message when posting a chat message then responds 400`() = chatApiTest { client ->
        val response = client.post("/api/v1/chat") {
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(message = "   "))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.body<ErrorResponse>().message.contains("blank"))
    }

    @Test
    fun `given a body without message when posting a chat message then responds 400`() = chatApiTest { client ->
        val response = client.post("/api/v1/chat") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `given an api key when application starts then assistant installs and task api works`() = testApplication {
        application { module(openCodeApiKey = "test-key") }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val response = client.get("/api/v1/tasks")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `given an agent failure when handling a request then responds 502 with cause`() = testApplication {
        application {
            module(openCodeApiKey = null)
            routing {
                get("/test/agent-failure") {
                    throw AIAgentException("Error from client: OpenAILLMClient: Invalid API key")
                }
            }
        }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val response = client.get("/test/agent-failure")

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertTrue(response.body<ErrorResponse>().message.contains("Invalid API key"))
    }

    @Test
    fun `given no api key when using the task api then behaves as without assistant`() = chatApiTest { client ->
        val createResponse = client.post("/api/v1/tasks") {
            contentType(ContentType.Application.Json)
            setBody(CreateTaskRequest(title = "Pay rent"))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        val listResponse = client.get("/api/v1/tasks")
        assertEquals(HttpStatusCode.OK, listResponse.status)
        assertEquals(listOf("Pay rent"), listResponse.body<List<TaskResponse>>().map { it.title })
    }
}
