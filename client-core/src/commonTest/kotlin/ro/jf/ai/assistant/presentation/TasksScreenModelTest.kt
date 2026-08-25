package ro.jf.ai.assistant.presentation

import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import ro.jf.ai.assistant.client.TasksApiClient
import ro.jf.ai.assistant.client.jsonHeaders
import ro.jf.ai.assistant.client.testApiHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TasksScreenModelTest {
    private fun modelOn(handler: MockRequestHandler) =
        TasksScreenModel(
            TasksApiClient(testApiHttpClient(handler), "http://test"),
            CoroutineScope(Dispatchers.Default),
        )

    private suspend fun settle() = withContext(Dispatchers.Default) { delay(200) }

    private fun taskJson(
        id: String,
        title: String,
        completed: Boolean = false,
    ) = """{"id":"$id","title":"$title","dueDate":null,"category":null,"completed":$completed}"""

    @Test
    fun `given tasks on the server when refreshing then the state holds them`() =
        runTest {
            val model = modelOn { respond("[${taskJson("t1", "Pay rent")}]", HttpStatusCode.OK, jsonHeaders()) }

            model.refresh()
            val state = model.state.first { it.tasks.isNotEmpty() }

            assertEquals("Pay rent", state.tasks.first().title)
            assertEquals(false, state.loading)
            assertNull(state.error)
        }

    @Test
    fun `given a filter when set then only matching tasks are visible`() =
        runTest {
            val model =
                modelOn {
                    respond(
                        "[${taskJson("t1", "Pay rent")},${taskJson("t2", "Dentist")}]",
                        HttpStatusCode.OK,
                        jsonHeaders(),
                    )
                }
            model.refresh()
            model.state.first { it.tasks.size == 2 }

            model.setFilter("dent")

            assertEquals(
                listOf("Dentist"),
                model.state.value.visibleTasks
                    .map { it.title },
            )
            assertEquals(2, model.state.value.tasks.size)
        }

    @Test
    fun `given a successful create when creating then the task appears in the list`() =
        runTest {
            val model = modelOn { respond(taskJson("t9", "New task"), HttpStatusCode.Created, jsonHeaders()) }

            model.create("New task")
            val state = model.state.first { it.tasks.isNotEmpty() }

            assertEquals(listOf("New task"), state.tasks.map { it.title })
        }

    @Test
    fun `given a task when toggling completion then a full-replace update preserves other fields`() =
        runTest {
            val requests = mutableListOf<String>()
            val model =
                modelOn { request ->
                    if (request.method == HttpMethod.Put) requests.add((request.body as TextContent).text)
                    when (request.method) {
                        HttpMethod.Get -> respond("[${taskJson("t1", "Pay rent")}]", HttpStatusCode.OK, jsonHeaders())
                        else -> respond(taskJson("t1", "Pay rent", completed = true), HttpStatusCode.OK, jsonHeaders())
                    }
                }
            model.refresh()
            val loaded = model.state.first { it.tasks.isNotEmpty() }

            model.toggleCompleted(loaded.tasks.first())
            val updated = model.state.first { it.tasks.firstOrNull()?.completed == true }

            assertEquals(
                listOf("""{"title":"Pay rent","dueDate":null,"category":null,"completed":true}"""),
                requests,
            )
            assertTrue(updated.tasks.first().completed)
        }

    @Test
    fun `given a pending delete when confirmed then the task is deleted and removed`() =
        runTest {
            var deletes = 0
            val model =
                modelOn { request ->
                    when (request.method) {
                        HttpMethod.Get -> {
                            respond("[${taskJson("t1", "Pay rent")}]", HttpStatusCode.OK, jsonHeaders())
                        }

                        HttpMethod.Delete -> {
                            deletes++
                            respond("", HttpStatusCode.NoContent)
                        }

                        else -> {
                            respond("", HttpStatusCode.BadRequest)
                        }
                    }
                }
            model.refresh()
            val loaded = model.state.first { it.tasks.isNotEmpty() }

            model.requestDelete(loaded.tasks.first())
            model.confirmDelete()
            val state = model.state.first { it.tasks.isEmpty() }

            assertEquals(1, deletes)
            assertNull(state.pendingDelete)
        }

    @Test
    fun `given a pending delete when cancelled then no request is sent and the task stays`() =
        runTest {
            var deletes = 0
            val model =
                modelOn { request ->
                    when (request.method) {
                        HttpMethod.Get -> {
                            respond("[${taskJson("t1", "Pay rent")}]", HttpStatusCode.OK, jsonHeaders())
                        }

                        else -> {
                            deletes++
                            respond("", HttpStatusCode.NoContent)
                        }
                    }
                }
            model.refresh()
            model.state.first { it.tasks.isNotEmpty() }

            model.requestDelete(
                model.state.value.tasks
                    .first(),
            )
            model.cancelDelete()
            settle()

            assertEquals(0, deletes)
            assertEquals(1, model.state.value.tasks.size)
            assertNull(model.state.value.pendingDelete)
        }

    @Test
    fun `given an error response when refreshing then the message is surfaced`() =
        runTest {
            val model = modelOn { respond("""{"message":"boom"}""", HttpStatusCode.InternalServerError, jsonHeaders()) }

            model.refresh()
            val state = model.state.first { it.error != null }

            assertEquals("boom", state.error)
            assertEquals(false, state.loading)
        }
}
