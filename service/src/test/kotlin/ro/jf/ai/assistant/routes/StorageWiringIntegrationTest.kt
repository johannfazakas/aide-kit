package ro.jf.ai.assistant.routes

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import org.koin.dsl.module
import ro.jf.ai.assistant.config.StartupConfig
import ro.jf.ai.assistant.config.serviceModule
import ro.jf.ai.assistant.exception.UnsupportedTaskOperationException
import ro.jf.ai.assistant.model.Task
import ro.jf.ai.assistant.module
import ro.jf.ai.assistant.repository.InMemoryTaskRepository
import ro.jf.ai.assistant.repository.TaskRepository
import ro.jf.ai.assistant.transfer.ErrorResponse
import ro.jf.ai.assistant.transfer.TaskResponse
import ro.jf.ai.assistant.transfer.UpdateTaskRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StorageWiringIntegrationTest {
    private class UnsupportedOpsRepository(
        private val delegate: InMemoryTaskRepository = InMemoryTaskRepository(),
    ) : TaskRepository by delegate {
        override fun update(
            id: String,
            title: String,
            dueDate: LocalDate?,
            topic: String?,
            done: Boolean,
        ): Task = throw UnsupportedTaskOperationException("Updating tasks")

        override fun delete(id: String): Boolean = throw UnsupportedTaskOperationException("Deleting tasks")
    }

    @Test
    fun `given default storage when listing tasks then the memory backend serves requests`() =
        testApplication {
            application { module(StartupConfig(openCodeApiKey = "test-key")) }
            val client = createClient { install(ContentNegotiation) { json() } }

            val response = client.get("/api/v1/tasks")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(emptyList(), response.body<List<TaskResponse>>())
        }

    @Test
    fun `given obsidian storage without configuration when starting then startup fails`() =
        testApplication {
            application { module(StartupConfig(openCodeApiKey = "test-key", taskStorage = "obsidian")) }

            assertFailsWith<IllegalArgumentException> { startApplication() }
        }

    @Test
    fun `given an invalid storage value when starting then startup fails`() =
        testApplication {
            application { module(StartupConfig(openCodeApiKey = "test-key", taskStorage = "postgres")) }

            assertFailsWith<IllegalArgumentException> { startApplication() }
        }

    @Test
    fun `given an unsupported-ops backend when updating then responds 501 with message`() =
        testApplication {
            val overrides = module { single<TaskRepository> { UnsupportedOpsRepository() } }
            application {
                module(StartupConfig(openCodeApiKey = "test-key"), koinModules = listOf(serviceModule(), overrides))
            }
            val client = createClient { install(ContentNegotiation) { json() } }

            val response =
                client.put("/api/v1/tasks/any-id") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateTaskRequest(title = "Title"))
                }

            assertEquals(HttpStatusCode.NotImplemented, response.status)
            assertTrue(response.body<ErrorResponse>().message.isNotBlank())
        }

    @Test
    fun `given an unsupported-ops backend when deleting then responds 501 with message`() =
        testApplication {
            val overrides = module { single<TaskRepository> { UnsupportedOpsRepository() } }
            application {
                module(StartupConfig(openCodeApiKey = "test-key"), koinModules = listOf(serviceModule(), overrides))
            }
            val client = createClient { install(ContentNegotiation) { json() } }

            val response = client.delete("/api/v1/tasks/any-id")

            assertEquals(HttpStatusCode.NotImplemented, response.status)
            assertTrue(response.body<ErrorResponse>().message.isNotBlank())
        }
}
