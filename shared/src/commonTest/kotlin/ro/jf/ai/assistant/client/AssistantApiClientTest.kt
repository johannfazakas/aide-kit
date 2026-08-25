package ro.jf.ai.assistant.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import ro.jf.ai.assistant.transfer.ChatRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssistantApiClientTest {
    private fun assistantApiClient(handler: MockRequestHandler) =
        AssistantApiClient(HttpClient(MockEngine(handler)), "http://test")

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `given a session id when chatting then the request carries it and the response yields the session`() =
        runTest {
            val client =
                assistantApiClient { request ->
                    assertEquals("http://test/api/v1/chat", request.url.toString())
                    assertEquals(
                        """{"message":"mark it as done","sessionId":"s1"}""",
                        (request.body as TextContent).text,
                    )
                    respond("""{"sessionId":"s1","reply":"Done!"}""", HttpStatusCode.OK, jsonHeaders())
                }

            val response = client.chat(ChatRequest(message = "mark it as done", sessionId = "s1"))

            assertEquals("s1", response.sessionId)
            assertEquals("Done!", response.reply)
        }

    @Test
    fun `given a degraded assistant when chatting then the status and message are surfaced`() =
        runTest {
            val client =
                assistantApiClient {
                    respond(
                        """{"message":"Assistant is not configured; set the OPENCODE_API_KEY environment variable"}""",
                        HttpStatusCode.ServiceUnavailable,
                        jsonHeaders(),
                    )
                }

            val exception = assertFailsWith<ApiException> { client.chat(ChatRequest(message = "hi")) }

            assertEquals(503, exception.status)
            assertEquals(
                "Assistant is not configured; set the OPENCODE_API_KEY environment variable",
                exception.message,
            )
        }
}
