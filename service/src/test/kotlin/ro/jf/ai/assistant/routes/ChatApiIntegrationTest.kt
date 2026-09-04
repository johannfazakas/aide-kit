package ro.jf.ai.assistant.routes

import ai.koog.agents.core.agent.exception.AIAgentException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import ro.jf.ai.assistant.config.StartupConfig
import ro.jf.ai.assistant.module
import ro.jf.ai.assistant.transfer.ChatRequest
import ro.jf.ai.assistant.transfer.CreateTaskRequest
import ro.jf.ai.assistant.transfer.ErrorResponse
import ro.jf.ai.assistant.transfer.TaskResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChatApiIntegrationTest {
    private fun chatApiTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { module(StartupConfig(openCodeApiKey = "test-key")) }
            val client =
                createClient {
                    install(ContentNegotiation) { json() }
                }
            block(client)
        }

    @Test
    fun `given no api key when starting then startup fails naming the variable`() =
        testApplication {
            application {
                val failure = assertFailsWith<IllegalArgumentException> { module(StartupConfig(openCodeApiKey = null)) }
                assertTrue(failure.message!!.contains("OPENCODE_API_KEY"))
            }
            startApplication()
        }

    @Test
    fun `given a blank api key when starting then startup fails naming the variable`() =
        testApplication {
            application {
                val failure =
                    assertFailsWith<IllegalArgumentException> { module(StartupConfig(openCodeApiKey = "   ")) }
                assertTrue(failure.message!!.contains("OPENCODE_API_KEY"))
            }
            startApplication()
        }

    @Test
    fun `given a blank message when posting a chat message then responds 400`() =
        chatApiTest { client ->
            val response =
                client.post("/api/v1/chat") {
                    contentType(ContentType.Application.Json)
                    setBody(ChatRequest(message = "   "))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.body<ErrorResponse>().message.contains("blank"))
        }

    @Test
    fun `given a body without message when posting a chat message then responds 400`() =
        chatApiTest { client ->
            val response =
                client.post("/api/v1/chat") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `given an agent failure when handling a request then responds 502 with cause`() =
        testApplication {
            application {
                module(StartupConfig(openCodeApiKey = "test-key"))
                routing {
                    get("/test/agent-failure") {
                        throw AIAgentException("Error from client: OpenAILLMClient: Invalid API key")
                    }
                }
            }
            val client =
                createClient {
                    install(ContentNegotiation) { json() }
                }

            val response = client.get("/test/agent-failure")

            assertEquals(HttpStatusCode.BadGateway, response.status)
            assertTrue(response.body<ErrorResponse>().message.contains("Invalid API key"))
        }

    @Test
    fun `given a configured assistant when using the task api then it works alongside chat`() =
        chatApiTest { client ->
            val createResponse =
                client.post("/api/v1/tasks") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTaskRequest(title = "Pay rent"))
                }
            assertEquals(HttpStatusCode.Created, createResponse.status)

            val listResponse = client.get("/api/v1/tasks")
            assertEquals(HttpStatusCode.OK, listResponse.status)
            assertEquals(listOf("Pay rent"), listResponse.body<List<TaskResponse>>().map { it.title })
        }
}
