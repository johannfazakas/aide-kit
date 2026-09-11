package ro.jf.ai.assistant.repository.obsidian

import kotlinx.datetime.LocalDate
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import ro.jf.ai.assistant.exception.UnsupportedTaskOperationException
import ro.jf.ai.assistant.repository.TaskPatch
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

    private fun bridgeOn(remote: File): Pair<VaultGitBridge, File> {
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
        return bridge to cloneDir
    }

    private fun repoOn(
        files: Map<String, String>,
        ids: List<String> = listOf("id000001", "id000002"),
    ): Pair<ObsidianTaskRepository, File> {
        val seeded =
            if (files.containsKey(registryPath)) {
                files
            } else {
                files + (registryPath to "---\ntopics: [home, health, finance, travel]\n---\n")
            }
        val (bridge, cloneDir) = bridgeOn(remoteSeededWith(seeded))
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

    private fun repoOnRemote(
        remote: File,
        ids: List<String> = listOf("id000001", "id000002"),
    ): Pair<ObsidianTaskRepository, File> {
        val (bridge, cloneDir) = bridgeOn(remote)
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

    private fun commitCountOf(remote: File): Int = Git.open(remote).use { it.log().call().count() }

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
        val (repository, cloneDir) = repoOnRemote(remote, ids = listOf("cccc3333"))
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
    fun `given a CRLF topic file when creating then the appended block keeps CRLF terminators`() {
        val (repository, cloneDir) =
            repoOn(
                mapOf(
                    ".gitattributes" to "* -text\n",
                    "areas/Home.md" to "---\r\ntopic: home\r\n---\r\n## Tasks\r\n",
                ),
                ids = listOf("crlf0001"),
            )

        repository.create("Buy milk", null, "home", false)

        assertEquals(
            "---\r\ntopic: home\r\n---\r\n## Tasks\r\n\r\n" +
                "- [ ] **Buy milk**\r\n" +
                "      [id:: crlf0001]\r\n",
            File(cloneDir, "areas/Home.md").readText(),
        )
    }

    @Test
    fun `given a lower-case tasks heading when creating then the existing section is reused`() {
        val (repository, cloneDir) =
            repoOn(
                mapOf("areas/Home.md" to "---\ntopic: home\n---\n## tasks\n"),
                ids = listOf("low00001"),
            )

        repository.create("Buy milk", null, "home", false)

        assertEquals(
            "---\ntopic: home\n---\n## tasks\n\n" +
                "- [ ] **Buy milk**\n" +
                "      [id:: low00001]\n",
            File(cloneDir, "areas/Home.md").readText(),
        )
    }

    private val homeTaskFile =
        "---\ntopic: home\n---\n## Tasks\n\n" +
            "- [ ] **Buy milk**\n" +
            "      [due:: 2026-09-05 14:00]\n" +
            "      [id:: f3k2a9aa]\n" +
            "      [rid:: weekly]\n"

    @Test
    fun `given a task with rid and due time when completing then only the checkbox character changes`() {
        val (repository, cloneDir) = repoOn(mapOf("areas/Home.md" to homeTaskFile))

        val updated = repository.update("f3k2a9aa", TaskPatch.complete())

        assertEquals(true, updated?.done)
        assertEquals(
            homeTaskFile.replace("- [ ]", "- [x]"),
            File(cloneDir, "areas/Home.md").readText(),
        )
    }

    @Test
    fun `given a done task when reopening then the checkbox reverts and nothing else changes`() {
        val (repository, cloneDir) =
            repoOn(mapOf("areas/Home.md" to homeTaskFile.replace("- [ ]", "- [x]")))

        val updated = repository.update("f3k2a9aa", TaskPatch.reopen())

        assertEquals(false, updated?.done)
        assertEquals(homeTaskFile, File(cloneDir, "areas/Home.md").readText())
    }

    @Test
    fun `given a due date change when updating then only the due line is rewritten`() {
        val (repository, cloneDir) = repoOn(mapOf("areas/Home.md" to homeTaskFile))

        repository.update("f3k2a9aa", TaskPatch.reschedule(LocalDate.parse("2026-09-06")))

        assertEquals(
            homeTaskFile.replace("[due:: 2026-09-05 14:00]", "[due:: 2026-09-06]"),
            File(cloneDir, "areas/Home.md").readText(),
        )
    }

    @Test
    fun `given a title change when updating then the title is rewritten in bold`() {
        val (repository, cloneDir) = repoOn(mapOf("areas/Home.md" to homeTaskFile))

        repository.update("f3k2a9aa", TaskPatch.rename("Buy oat milk"))

        assertEquals(
            homeTaskFile.replace("**Buy milk**", "**Buy oat milk**"),
            File(cloneDir, "areas/Home.md").readText(),
        )
    }

    @Test
    fun `given a task without an explicit id when completing then its derived id is stamped and kept`() {
        val (repository, cloneDir) =
            repoOn(mapOf(inboxPath to "# Inbox\n\n## Tasks\n\n- [ ] Call dentist\n"))
        val tasks = repository.findAll()
        val derivedId = tasks.single().id

        val updated = repository.update(derivedId, TaskPatch.complete())

        assertEquals(derivedId, updated?.id)
        assertEquals(
            "# Inbox\n\n## Tasks\n\n- [x] Call dentist\n      [id:: $derivedId]\n",
            File(cloneDir, inboxPath).readText(),
        )
        assertEquals(derivedId, repository.findById(derivedId)?.id)
    }

    @Test
    fun `given a topic change from a topic file when updating then the block moves to the new topic file`() {
        val (repository, cloneDir) =
            repoOn(
                mapOf(
                    "areas/Home.md" to homeTaskFile,
                    "areas/Health.md" to "---\ntopic: health\n---\n## Tasks\n",
                ),
            )

        val updated = repository.update("f3k2a9aa", TaskPatch.changeTopic("health"))

        assertEquals("health", updated?.topic)
        assertEquals("---\ntopic: home\n---\n## Tasks\n", File(cloneDir, "areas/Home.md").readText())
        assertEquals(
            "---\ntopic: health\n---\n## Tasks\n\n" +
                "- [ ] **Buy milk**\n" +
                "      [due:: 2026-09-05 14:00]\n" +
                "      [id:: f3k2a9aa]\n" +
                "      [rid:: weekly]\n",
            File(cloneDir, "areas/Health.md").readText(),
        )
    }

    @Test
    fun `given an inbox task moved to a fileless topic when updating then it stays with an inline topic`() {
        val (repository, cloneDir) =
            repoOn(
                mapOf(
                    inboxPath to "# Inbox\n\n## Tasks\n\n- [ ] Sort me\n      [id:: aaaa1111]\n",
                    registryPath to "---\ntopics: [health]\n---\n",
                ),
            )

        val updated = repository.update("aaaa1111", TaskPatch.changeTopic("health"))

        assertEquals("health", updated?.topic)
        assertEquals(
            "# Inbox\n\n## Tasks\n\n- [ ] Sort me\n      [id:: aaaa1111]\n      [topic:: health]\n",
            File(cloneDir, inboxPath).readText(),
        )
    }

    @Test
    fun `given an inbox task moved to a topic file when updating then the block relocates to that file`() {
        val (repository, cloneDir) =
            repoOn(
                mapOf(
                    inboxPath to "# Inbox\n\n## Tasks\n\n- [ ] Sort me\n      [id:: aaaa1111]\n",
                    "areas/Health.md" to "---\ntopic: health\n---\n## Tasks\n",
                ),
            )

        val updated = repository.update("aaaa1111", TaskPatch.changeTopic("health"))

        assertEquals("health", updated?.topic)
        assertEquals("# Inbox\n\n## Tasks\n", File(cloneDir, inboxPath).readText())
        assertEquals(
            "---\ntopic: health\n---\n## Tasks\n\n- [ ] Sort me\n      [id:: aaaa1111]\n",
            File(cloneDir, "areas/Health.md").readText(),
        )
    }

    @Test
    fun `given a note line in a block when moving topic then the note travels and nothing remains in the source`() {
        val homeWithNote =
            "---\ntopic: home\n---\n## Tasks\n\n" +
                "- [ ] Plan trip\n" +
                "      remember passports\n" +
                "      [id:: note1111]\n"
        val (repository, cloneDir) =
            repoOn(
                mapOf(
                    "areas/Home.md" to homeWithNote,
                    "areas/Health.md" to "---\ntopic: health\n---\n## Tasks\n",
                ),
            )

        repository.update("note1111", TaskPatch.changeTopic("health"))

        assertEquals("---\ntopic: home\n---\n## Tasks\n", File(cloneDir, "areas/Home.md").readText())
        assertEquals(
            "---\ntopic: health\n---\n## Tasks\n\n" +
                "- [ ] Plan trip\n" +
                "      remember passports\n" +
                "      [id:: note1111]\n",
            File(cloneDir, "areas/Health.md").readText(),
        )
    }

    @Test
    fun `given an inline checkbox-line due when rescheduling then it migrates to a follow-up line with no duplicate`() {
        val seed =
            "# Inbox\n\n## Tasks\n\n" +
                "- [ ] Pay rent [due:: 2026-09-01]\n" +
                "      [id:: e1e1e1e1]\n"
        val (repository, cloneDir) = repoOn(mapOf(inboxPath to seed))

        repository.update("e1e1e1e1", TaskPatch.reschedule(LocalDate.parse("2026-09-10")))

        assertEquals(
            "# Inbox\n\n## Tasks\n\n" +
                "- [ ] Pay rent\n" +
                "      [due:: 2026-09-10]\n" +
                "      [id:: e1e1e1e1]\n",
            File(cloneDir, inboxPath).readText(),
        )
    }

    @Test
    fun `given an unparseable due value when rescheduling with null then the due field is removed`() {
        val seed =
            "# Inbox\n\n## Tasks\n\n" +
                "- [ ] Someday task\n" +
                "      [due:: someday]\n" +
                "      [id:: d0d0d0d0]\n"
        val (repository, cloneDir) = repoOn(mapOf(inboxPath to seed))

        repository.update("d0d0d0d0", TaskPatch.reschedule(null))

        assertEquals(
            "# Inbox\n\n## Tasks\n\n" +
                "- [ ] Someday task\n" +
                "      [id:: d0d0d0d0]\n",
            File(cloneDir, inboxPath).readText(),
        )
    }

    @Test
    fun `given a topic with regex metacharacters when updating an inline topic then it is written verbatim`() {
        val seed =
            "# Inbox\n\n## Tasks\n\n" +
                "- [ ] Save up\n" +
                "      [id:: c4c4c4c4]\n" +
                "      [topic:: old]\n"
        val (repository, cloneDir) =
            repoOn(
                mapOf(
                    inboxPath to seed,
                    registryPath to "---\ntopics: [ca\$h]\n---\n",
                ),
            )

        val updated = repository.update("c4c4c4c4", TaskPatch.changeTopic("ca\$h"))

        assertEquals("ca\$h", updated?.topic)
        assertEquals(
            "# Inbox\n\n## Tasks\n\n" +
                "- [ ] Save up\n" +
                "      [id:: c4c4c4c4]\n" +
                "      [topic:: ca\$h]\n",
            File(cloneDir, inboxPath).readText(),
        )
    }

    @Test
    fun `given a CRLF file when completing then only the checkbox line changes and endings stay CRLF`() {
        val crlfFile =
            (
                "---\ntopic: home\n---\n## Tasks\n\n" +
                    "- [ ] **Buy milk**\n" +
                    "      [id:: f3k2a9aa]\n"
            ).replace("\n", "\r\n")
        val (repository, cloneDir) =
            repoOn(mapOf("areas/Home.md" to crlfFile, ".gitattributes" to "* -text\n"))

        repository.update("f3k2a9aa", TaskPatch.complete())

        assertEquals(
            crlfFile.replace("- [ ]", "- [x]"),
            File(cloneDir, "areas/Home.md").readText(),
        )
    }

    @Test
    fun `given an update when it succeeds then the remote gains exactly one commit`() {
        val remote = remoteSeededWith(mapOf("areas/Home.md" to homeTaskFile))
        val (repository, _) = repoOnRemote(remote)
        val before = commitCountOf(remote)

        repository.update("f3k2a9aa", TaskPatch.complete())

        assertEquals(before + 1, commitCountOf(remote))
    }

    @Test
    fun `given a no-op patch when updating then no commit is created`() {
        val remote = remoteSeededWith(mapOf("areas/Home.md" to homeTaskFile))
        val (repository, cloneDir) = repoOnRemote(remote)
        val before = commitCountOf(remote)

        val updated = repository.update("f3k2a9aa", TaskPatch.rename("Buy milk"))

        assertEquals("f3k2a9aa", updated?.id)
        assertEquals(before, commitCountOf(remote))
        assertEquals(homeTaskFile, File(cloneDir, "areas/Home.md").readText())
    }

    @Test
    fun `given a stale derived id when updating then null is returned and nothing changes`() {
        val remote = remoteSeededWith(mapOf(inboxPath to "# Inbox\n\n## Tasks\n\n- [ ] Old title\n"))
        val (repository, _) = repoOnRemote(remote)
        val tasks = repository.findAll()
        val staleId = tasks.single().id
        pushToRemote(remote, inboxPath, "# Inbox\n\n## Tasks\n\n- [ ] New title\n")
        val before = commitCountOf(remote)

        assertNull(repository.update(staleId, TaskPatch.complete()))
        assertEquals(before, commitCountOf(remote))
    }

    @Test
    fun `given an unknown id when updating then null is returned`() {
        val (repository, _) = repoOn(mapOf("areas/Home.md" to homeTaskFile))

        assertNull(repository.update("missing0", TaskPatch.complete()))
    }

    @Test
    fun `given obsidian storage when deleting then it is unsupported`() {
        val (repository, _) = repoOn(mapOf("areas/Home.md" to "---\ntopic: home\n---\n## Tasks\n"))

        assertFailsWith<UnsupportedTaskOperationException> { repository.delete("id000001") }
    }
}
