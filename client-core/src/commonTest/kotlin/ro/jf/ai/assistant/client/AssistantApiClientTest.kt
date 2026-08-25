package ro.jf.ai.assistant.client

import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import ro.jf.ai.assistant.transfer.ChatRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssistantApiClientTest {
    private fun assistantApiClient(handler: MockRequestHandler) =
        AssistantApiClient(testApiHttpClient(handler), "http://test")

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
    fun `given a gateway failure when chatting then the status and message are surfaced`() =
        runTest {
            val client =
                assistantApiClient {
                    respond(
                        """{"message":"LLM gateway failure"}""",
                        HttpStatusCode.BadGateway,
                        jsonHeaders(),
                    )
                }

            val exception = assertFailsWith<ApiException> { client.chat(ChatRequest(message = "hi")) }

            assertEquals(502, exception.status)
            assertEquals("LLM gateway failure", exception.message)
        }
}
