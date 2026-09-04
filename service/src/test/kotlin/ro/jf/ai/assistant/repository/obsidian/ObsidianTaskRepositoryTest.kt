package ro.jf.ai.assistant.repository.obsidian

import kotlinx.datetime.LocalDate
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import ro.jf.ai.assistant.exception.UnsupportedTaskOperationException
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ObsidianTaskRepositoryTest {
    private val inboxPath = "organization/Inbox.md"
    private val registryPath = "organization/Topics.md"

    private fun tempDir(name: String): File = Files.createTempDirectory(name).toFile()

    private fun remoteSeededWith(files: Map<String, String>): File {
        val remote = tempDir("remote")
        Git
            .init()
            .setBare(true)
            .setDirectory(remote)
            .setInitialBranch("main")
            .call()
            .close()
        val seedDir = tempDir("seed")
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

    private fun pushToRemote(
        remote: File,
        path: String,
        content: String,
    ) {
        val dir = tempDir("device")
        Git.cloneRepository().setURI(remote.toURI().toString()).setDirectory(dir).call().use { git ->
            File(dir, path).apply {
                parentFile.mkdirs()
                writeText(content)
            }
            git.add().addFilepattern(".").call()
            git.commit().setMessage("device: add $path").call()
            git.push().call()
        }
    }

    private fun repoOn(
        files: Map<String, String>,
        ids: List<String> = listOf("id000001", "id000002"),
    ): Pair<ObsidianTaskRepository, File> {
        val remote = remoteSeededWith(files)
        val cloneDir = File(tempDir("host"), "clone")
        val bridge =
            VaultGitBridge(
                ObsidianConfig(
                    repoUrl = remote.toURI().toString(),
                    token = null,
                    branch = "main",
                    cloneDir = cloneDir,
                    inboxPath = inboxPath,
                    registryPath = registryPath,
                ),
            )
        val idQueue = ArrayDeque(ids)
        val repository =
            ObsidianTaskRepository(
                bridge = bridge,
                scanner = VaultScanner(inboxPath = inboxPath, registryPath = registryPath),
                inboxPath = inboxPath,
                idGenerator = { idQueue.removeFirst() },
            )
        return repository to cloneDir
    }

    @Test
    fun `given a topic file when creating a task with a due date then the block omits the inherited topic`() {
        val (repository, cloneDir) =
            repoOn(
                mapOf("areas/Finance.md" to "---\ntopic: finance\n---\n## Tasks\n"),
                ids = listOf("k7f3q9d2"),
            )

        repository.create("Pay rent", LocalDate.parse("2026-09-01"), "finance", false)

        assertEquals(
            "---\ntopic: finance\n---\n## Tasks\n\n" +
                "- [ ] **Pay rent**\n" +
                "      [due:: 2026-09-01]\n" +
                "      [id:: k7f3q9d2]\n",
            File(cloneDir, "areas/Finance.md").readText(),
        )
    }

    @Test
    fun `given no topic when creating then the task lands plain in the inbox`() {
        val (repository, cloneDir) =
            repoOn(
                mapOf(inboxPath to "# Inbox\n"),
                ids = listOf("aaaa1111"),
            )

        repository.create("Sort me later", null, null, false)

        assertEquals(
            "# Inbox\n\n## Tasks\n\n" +
                "- [ ] **Sort me later**\n" +
                "      [id:: aaaa1111]\n",
            File(cloneDir, inboxPath).readText(),
        )
    }

    @Test
    fun `given a registry topic without a file when creating then the inbox block carries an inline topic`() {
        val (repository, cloneDir) =
            repoOn(
                mapOf(
                    inboxPath to "# Inbox\n",
                    registryPath to "---\ntopics: [finance, travel]\n---\n",
                ),
                ids = listOf("bbbb2222"),
            )

        repository.create("Book flight", null, "travel", false)

        assertEquals(
            "# Inbox\n\n## Tasks\n\n" +
                "- [ ] **Book flight**\n" +
                "      [id:: bbbb2222]\n" +
                "      [topic:: travel]\n",
            File(cloneDir, inboxPath).readText(),
        )
    }

    @Test
    fun `given a topic file without a tasks heading when creating twice then the heading is created once and reused`() {
        val (repository, cloneDir) =
            repoOn(
                mapOf("areas/Home.md" to "---\ntopic: home\n---\n"),
                ids = listOf("id111111", "id222222"),
            )

        repository.create("First", null, "home", false)
        repository.create("Second", null, "home", false)

        assertEquals(
            "---\ntopic: home\n---\n\n## Tasks\n\n" +
                "- [ ] **First**\n" +
                "      [id:: id111111]\n\n" +
                "- [ ] **Second**\n" +
                "      [id:: id222222]\n",
            File(cloneDir, "areas/Home.md").readText(),
        )
    }

    @Test
    fun `given a created task when listing and getting then it is found`() {
        val (repository, _) =
            repoOn(
                mapOf("areas/Home.md" to "---\ntopic: home\n---\n## Tasks\n"),
                ids = listOf("id333333"),
            )

        val created = repository.create("Buy milk", null, "home", false)

        assertEquals(listOf("Buy milk"), repository.findAll().map { it.title })
        assertEquals("Buy milk", repository.findById(created.id)?.title)
        assertNull(repository.findById("missing0"))
    }

    @Test
    fun `given a topic file added to the remote after clone when creating then create pulls and files into it`() {
        val remote =
            remoteSeededWith(
                mapOf(
                    inboxPath to "# Inbox\n",
                    registryPath to "---\ntopics: [finance]\n---\n",
                ),
            )
        val cloneDir = File(tempDir("host"), "clone")
        val bridge =
            VaultGitBridge(
                ObsidianConfig(
                    repoUrl = remote.toURI().toString(),
                    token = null,
                    branch = "main",
                    cloneDir = cloneDir,
                    inboxPath = inboxPath,
                    registryPath = registryPath,
                ),
            )
        val repository =
            ObsidianTaskRepository(
                bridge = bridge,
                scanner = VaultScanner(inboxPath = inboxPath, registryPath = registryPath),
                inboxPath = inboxPath,
                idGenerator = { "cccc3333" },
            )
        pushToRemote(remote, "areas/Finance.md", "---\ntopic: finance\n---\n## Tasks\n")

        repository.create("Pay rent", null, "finance", false)

        assertEquals(
            "---\ntopic: finance\n---\n## Tasks\n\n" +
                "- [ ] **Pay rent**\n" +
                "      [id:: cccc3333]\n",
            File(cloneDir, "areas/Finance.md").readText(),
        )
    }

    @Test
    fun `given a registry note when listing topics then the registry values are returned`() {
        val (repository, _) = repoOn(mapOf(registryPath to "---\ntopics: [finance, travel]\n---\n"))

        assertEquals(listOf("finance", "travel"), repository.listTopics())
    }

    @Test
    fun `given obsidian storage when updating then it is unsupported`() {
        val (repository, _) = repoOn(mapOf("areas/Home.md" to "---\ntopic: home\n---\n## Tasks\n"))

        assertFailsWith<UnsupportedTaskOperationException> {
            repository.update("id000001", "Title", null, "home", true)
        }
    }

    @Test
    fun `given obsidian storage when deleting then it is unsupported`() {
        val (repository, _) = repoOn(mapOf("areas/Home.md" to "---\ntopic: home\n---\n## Tasks\n"))

        assertFailsWith<UnsupportedTaskOperationException> { repository.delete("id000001") }
    }
}
