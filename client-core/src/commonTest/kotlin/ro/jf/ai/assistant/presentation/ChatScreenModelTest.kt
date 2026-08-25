package ro.jf.ai.assistant.presentation

import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import ro.jf.ai.assistant.client.AssistantApiClient
import ro.jf.ai.assistant.client.jsonHeaders
import ro.jf.ai.assistant.client.testApiHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatScreenModelTest {
    private fun modelOn(handler: MockRequestHandler) =
        ChatScreenModel(
            AssistantApiClient(testApiHttpClient(handler), "http://test"),
            CoroutineScope(Dispatchers.Default),
        )

    @Test
    fun `given a conversation when sending messages then the session id from the first reply is carried forward`() =
        runTest {
            val bodies = mutableListOf<String>()
            val model =
                modelOn { request ->
                    bodies.add((request.body as TextContent).text)
                    respond("""{"sessionId":"s1","reply":"reply ${bodies.size}"}""", HttpStatusCode.OK, jsonHeaders())
                }

            model.send("create a task")
            model.state.first { it.transcript.size == 2 }
            model.send("mark it as done")
            val state = model.state.first { it.transcript.size == 4 }

            assertEquals("""{"message":"create a task","sessionId":null}""", bodies[0])
            assertEquals("""{"message":"mark it as done","sessionId":"s1"}""", bodies[1])
            assertEquals(
                listOf(ChatRole.USER, ChatRole.ASSISTANT, ChatRole.USER, ChatRole.ASSISTANT),
                state.transcript.map { it.role },
            )
            assertNull(state.error)
        }

    @Test
    fun `given an error response when sending then the error is surfaced and sending stops`() =
        runTest {
            val model =
                modelOn { respond("""{"message":"LLM gateway failure"}""", HttpStatusCode.BadGateway, jsonHeaders()) }

            model.send("hello")
            val state = model.state.first { it.error != null }

            assertEquals("LLM gateway failure", state.error)
            assertEquals(false, state.sending)
            assertEquals(1, state.transcript.size)
        }

    @Test
    fun `given a blank message when sending then nothing happens`() =
        runTest {
            var calls = 0
            val model =
                modelOn {
                    calls++
                    respond("""{"sessionId":"s1","reply":"r"}""", HttpStatusCode.OK, jsonHeaders())
                }

            model.send("   ")
            withContext(Dispatchers.Default) { delay(200) }

            assertEquals(0, calls)
            assertEquals(0, model.state.value.transcript.size)
        }
}
