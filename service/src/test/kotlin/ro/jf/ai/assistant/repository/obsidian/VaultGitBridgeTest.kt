package ro.jf.ai.assistant.repository.obsidian

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import ro.jf.ai.assistant.exception.VaultConflictException
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultGitBridgeTest {
    private fun tempDir(name: String): File = Files.createTempDirectory(name).toFile()

    private fun bareRemoteWithSeed(seedContent: String = "---\ntopic: home\n---\n## Tasks\n"): File {
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
            File(seedDir, "areas/Home.md").apply {
                parentFile.mkdirs()
                writeText(seedContent)
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

    private fun workingCloneOf(remote: File): Pair<Git, File> {
        val dir = tempDir("worker")
        val git =
            Git
                .cloneRepository()
                .setURI(remote.toURI().toString())
                .setDirectory(dir)
                .call()
        return git to dir
    }

    private fun commitCountOf(remote: File): Int = Git.open(remote).use { it.log().call().count() }

    private fun bridgeOn(
        remote: File,
        cloneDir: File,
    ) = VaultGitBridge(
        ObsidianConfig(repoUrl = remote.toURI().toString(), token = null, branch = "main", cloneDir = cloneDir),
    )

    @Test
    fun `given no clone when the bridge is created then the vault is cloned`() {
        val remote = bareRemoteWithSeed()
        val cloneDir = File(tempDir("host"), "clone")

        bridgeOn(remote, cloneDir)

        assertTrue(File(cloneDir, ".git").isDirectory)
        assertTrue(File(cloneDir, "areas/Home.md").isFile)
    }

    @Test
    fun `given an existing clone when a new bridge is created then it is reused`() {
        val remote = bareRemoteWithSeed()
        val cloneDir = File(tempDir("host"), "clone")
        bridgeOn(remote, cloneDir)
        val marker = File(cloneDir, "marker.txt").apply { writeText("kept") }

        bridgeOn(remote, cloneDir)

        assertTrue(marker.exists())
    }

    @Test
    fun `given a remote change when pulling then it becomes visible`() {
        val remote = bareRemoteWithSeed()
        val cloneDir = File(tempDir("host"), "clone")
        val bridge = bridgeOn(remote, cloneDir)
        val (worker, workerDir) = workingCloneOf(remote)
        File(workerDir, "areas/Work.md").writeText("---\ntopic: work\n---\n## Tasks\n")
        worker.add().addFilepattern(".").call()
        worker.commit().setMessage("add work file").call()
        worker.push().call()
        worker.close()

        val pulled = bridge.pull()

        assertTrue(pulled)
        assertTrue(bridge.markdownFiles().any { it.relativePath == "areas/Work.md" })
    }

    @Test
    fun `given a create when writing then the remote gains exactly one commit`() {
        val remote = bareRemoteWithSeed()
        val cloneDir = File(tempDir("host"), "clone")
        val bridge = bridgeOn(remote, cloneDir)
        val before = commitCountOf(remote)

        bridge.write("agent: add task \"Pay rent\"") {
            File(cloneDir, "areas/Home.md").appendText("- [ ] Pay rent\n")
        }

        assertEquals(before + 1, commitCountOf(remote))
    }

    @Test
    fun `given a conflicting remote edit when writing then it aborts and leaves a clean clone`() {
        val remote = bareRemoteWithSeed(seedContent = "---\ntopic: home\n---\n## Tasks\nLINE\n")
        val cloneDir = File(tempDir("host"), "clone")
        val bridge = bridgeOn(remote, cloneDir)
        val (worker, workerDir) = workingCloneOf(remote)
        File(workerDir, "areas/Home.md").writeText("---\ntopic: home\n---\n## Tasks\nREMOTE\n")
        worker.add().addFilepattern(".").call()
        worker.commit().setMessage("remote edit").call()
        worker.push().call()
        worker.close()

        assertFailsWith<VaultConflictException> {
            bridge.write("agent: add task") {
                File(cloneDir, "areas/Home.md").writeText("---\ntopic: home\n---\n## Tasks\nLOCAL\n")
            }
        }

        val content = File(cloneDir, "areas/Home.md").readText()
        assertFalse(content.contains("<<<<<<<"))
        assertTrue(content.contains("REMOTE"))
        assertFalse(content.contains("LOCAL"))
        Git.open(cloneDir).use { assertTrue(it.status().call().isClean) }
    }
}
