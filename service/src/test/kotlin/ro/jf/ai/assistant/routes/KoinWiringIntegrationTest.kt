package ro.jf.ai.assistant.routes

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.koin.dsl.module
import ro.jf.ai.assistant.config.serviceModule
import ro.jf.ai.assistant.module
import ro.jf.ai.assistant.repository.InMemoryTaskRepository
import ro.jf.ai.assistant.repository.TaskRepository
import ro.jf.ai.assistant.transfer.CreateTaskRequest
import ro.jf.ai.assistant.transfer.TaskResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class KoinWiringIntegrationTest {
    @Test
    fun `given an overriding koin module with a seeded repository when listing tasks then serves the seeded tasks`() =
        testApplication {
            val seeded =
                InMemoryTaskRepository().apply {
                    create(title = "Seeded task", dueDate = null, category = "seed", completed = false)
                }
            val overrides = module { single<TaskRepository> { seeded } }
            application {
                module(openCodeApiKey = "test-key", koinModules = listOf(serviceModule, overrides))
            }
            val client = createClient { install(ContentNegotiation) { json() } }

            val tasks = client.get("/api/v1/tasks").body<List<TaskResponse>>()

            assertEquals(1, tasks.size)
            assertEquals("Seeded task", tasks.first().title)
            assertEquals("seed", tasks.first().category)
        }

    @Test
    fun `given default modules when creating a task then subsequent reads observe the same singleton store`() =
        testApplication {
            application { module(openCodeApiKey = "test-key") }
            val client = createClient { install(ContentNegotiation) { json() } }

            val created =
                client
                    .post("/api/v1/tasks") {
                        contentType(ContentType.Application.Json)
                        setBody(CreateTaskRequest(title = "Shared store"))
                    }.body<TaskResponse>()

            val fetched = client.get("/api/v1/tasks/${created.id}")
            assertEquals(HttpStatusCode.OK, fetched.status)
            assertEquals(created, fetched.body<TaskResponse>())
        }
}
