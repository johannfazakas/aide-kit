package ro.jf.ai.assistant.transfer

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class TransferModelsSerializationTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `given a task response with due date when serialized then the wire format matches the rest contract`() {
        val response =
            TaskResponse(
                id = "t1",
                title = "Pay rent",
                dueDate = LocalDate.parse("2026-08-24"),
                topic = "home",
                done = false,
            )

        val encoded = json.encodeToString(TaskResponse.serializer(), response)

        assertEquals(
            """{"id":"t1","title":"Pay rent","dueDate":"2026-08-24","topic":"home","done":false}""",
            encoded,
        )
    }

    @Test
    fun `given a task response without optional fields when serialized then nulls are encoded explicitly`() {
        val response = TaskResponse(id = "t1", title = "Pay rent", done = true)

        val encoded = json.encodeToString(TaskResponse.serializer(), response)

        assertEquals("""{"id":"t1","title":"Pay rent","dueDate":null,"topic":null,"done":true}""", encoded)
    }

    @Test
    fun `given a create request with due date when round-tripped then values are preserved`() {
        val request = CreateTaskRequest(title = "Dentist", dueDate = LocalDate.parse("2026-09-01"), topic = "health")

        val decoded =
            json.decodeFromString(
                CreateTaskRequest.serializer(),
                json.encodeToString(CreateTaskRequest.serializer(), request),
            )

        assertEquals(request, decoded)
    }

    @Test
    fun `given an update request without optional fields when round-tripped then defaults are preserved`() {
        val request = UpdateTaskRequest(title = "Dentist", done = true)

        val decoded =
            json.decodeFromString(
                UpdateTaskRequest.serializer(),
                json.encodeToString(UpdateTaskRequest.serializer(), request),
            )

        assertEquals(request, decoded)
    }

    @Test
    fun `given chat models when round-tripped then session and message fields are preserved`() {
        val request = ChatRequest(message = "mark it as done", sessionId = "s1")
        val response = ChatResponse(sessionId = "s1", reply = "Done!")

        val decodedRequest =
            json.decodeFromString(ChatRequest.serializer(), json.encodeToString(ChatRequest.serializer(), request))
        val decodedResponse =
            json.decodeFromString(ChatResponse.serializer(), json.encodeToString(ChatResponse.serializer(), response))

        assertEquals(request, decodedRequest)
        assertEquals(response, decodedResponse)
    }

    @Test
    fun `given an error response when round-tripped then the message is preserved`() {
        val error = ErrorResponse(message = "Task with id t1 not found")

        val decoded =
            json.decodeFromString(ErrorResponse.serializer(), json.encodeToString(ErrorResponse.serializer(), error))

        assertEquals(error, decoded)
    }
}
