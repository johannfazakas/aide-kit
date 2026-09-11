package ro.jf.ai.assistant.repository.obsidian

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import ro.jf.ai.assistant.repository.TaskRepositoryContractTest
import ro.jf.ai.assistant.service.TaskService
import java.io.File
import java.nio.file.Files

class ObsidianTaskRepositoryContractTest : TaskRepositoryContractTest() {
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

    override fun newService(): TaskService {
        val remote =
            remoteSeededWith(
                mapOf(
                    inboxPath to "# Inbox\n",
                    registryPath to "---\ntopics: [home, health]\n---\n",
                    "areas/Home.md" to "---\ntopic: home\n---\n## Tasks\n",
                    "areas/Health.md" to "---\ntopic: health\n---\n## Tasks\n",
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
            )
        return TaskService(repository)
    }
}
