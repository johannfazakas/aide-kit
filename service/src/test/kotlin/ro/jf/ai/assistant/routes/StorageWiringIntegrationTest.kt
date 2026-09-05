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
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.koin.dsl.module
import ro.jf.ai.assistant.config.StartupConfig
import ro.jf.ai.assistant.config.serviceModule
import ro.jf.ai.assistant.exception.UnsupportedTaskOperationException
import ro.jf.ai.assistant.module
import ro.jf.ai.assistant.repository.InMemoryTaskRepository
import ro.jf.ai.assistant.repository.TaskRepository
import ro.jf.ai.assistant.transfer.ErrorResponse
import ro.jf.ai.assistant.transfer.TaskResponse
import ro.jf.ai.assistant.transfer.UpdateTaskRequest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StorageWiringIntegrationTest {
    private class UnsupportedOpsRepository(
        private val delegate: InMemoryTaskRepository = InMemoryTaskRepository(),
    ) : TaskRepository by delegate {
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

    private fun obsidianRemoteSeededWith(files: Map<String, String>): File {
        val remote = Files.createTempDirectory("remote").toFile()
        Git
            .init()
            .setBare(true)
            .setDirectory(remote)
            .setInitialBranch("main")
            .call()
            .close()
        val seedDir = Files.createTempDirectory("seed").toFile()
        Git.init().setDirectory(seedDir).setInitialBranch("main").call().use { seed ->
            files.forEach { (path, content) ->
                File(seedDir, path).apply {
                    parentFile.mkdirs()
                    writeText(content)
                }
            }
            seed.add().addFilepattern(".").call()
            seed.commit().setMessage("seed").call()
            seed
                .remoteAdd()
                .setName("origin")
                .setUri(URIish(remote.toURI().toString()))
                .call()
            seed.push().setRemote("origin").call()
        }
        return remote
    }

    @Test
    fun `given obsidian storage when updating via PUT then the vault reflects the change`() =
        testApplication {
            val remote =
                obsidianRemoteSeededWith(
                    mapOf(
                        "organization/Topics.md" to "---\ntopics: [home]\n---\n",
                        "areas/Home.md" to
                            "---\ntopic: home\n---\n## Tasks\n\n- [ ] **Buy milk**\n      [id:: f3k2a9aa]\n",
                    ),
                )
            val cloneDir = File(Files.createTempDirectory("host").toFile(), "clone")
            application {
                module(
                    StartupConfig(
                        openCodeApiKey = "test-key",
                        taskStorage = "obsidian",
                        obsidianRepoUrl = remote.toURI().toString(),
                        obsidianRepoToken = "unused",
                        obsidianCloneDir = cloneDir.path,
                    ),
                )
            }
            val client = createClient { install(ContentNegotiation) { json() } }

            val response =
                client.put("/api/v1/tasks/f3k2a9aa") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateTaskRequest(title = "Buy milk", topic = "home", done = true))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.body<TaskResponse>().done)
            assertTrue(File(cloneDir, "areas/Home.md").readText().contains("- [x] **Buy milk**"))
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
