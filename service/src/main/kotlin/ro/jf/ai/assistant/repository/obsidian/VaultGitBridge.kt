package ro.jf.ai.assistant.repository.obsidian

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.slf4j.LoggerFactory
import ro.jf.ai.assistant.exception.VaultConflictException
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class ObsidianConfig(
    val repoUrl: String,
    val token: String?,
    val branch: String = "main",
    val cloneDir: File,
    val inboxPath: String = DEFAULT_INBOX_PATH,
    val registryPath: String = DEFAULT_REGISTRY_PATH,
)

class VaultGitBridge(
    private val config: ObsidianConfig,
) {
    private val logger = LoggerFactory.getLogger(VaultGitBridge::class.java)
    private val lock = ReentrantLock()
    private val credentials: CredentialsProvider? =
        config.token?.takeIf { it.isNotBlank() }?.let { UsernamePasswordCredentialsProvider(it, "") }

    private val git: Git

    val root: File get() = config.cloneDir

    init {
        git =
            if (File(config.cloneDir, ".git").isDirectory) {
                logger.info("Reusing existing vault clone at {}", config.cloneDir)
                Git.open(config.cloneDir)
            } else {
                logger.info("Cloning vault into {}", config.cloneDir)
                config.cloneDir.parentFile?.mkdirs()
                Git
                    .cloneRepository()
                    .setURI(config.repoUrl)
                    .setDirectory(config.cloneDir)
                    .setBranch(config.branch)
                    .setCredentialsProvider(credentials)
                    .call()
            }
    }

    fun pull(): Boolean =
        runCatching {
            val result =
                git
                    .pull()
                    .setRemoteBranchName(config.branch)
                    .setRebase(true)
                    .setCredentialsProvider(credentials)
                    .call()
            val ok = result.isSuccessful && (result.rebaseResult?.status?.isSuccessful ?: true)
            if (!ok) abortRebaseAndReset()
            ok
        }.getOrElse {
            logger.warn("Vault pull failed; serving local state", it)
            abortRebaseAndReset()
            false
        }

    fun <T> read(block: () -> T): T =
        lock.withLock {
            pull()
            block()
        }

    fun markdownFiles(): List<VaultFile> =
        config.cloneDir
            .walkTopDown()
            .onEnter { it.name != ".git" }
            .filter { it.isFile && it.extension == "md" }
            .map { file -> VaultFile(relativePath = relativePath(file), content = file.readText()) }
            .toList()

    private fun relativePath(file: File): String =
        config.cloneDir
            .toPath()
            .relativize(file.toPath())
            .toString()
            .replace(File.separatorChar, '/')

    fun <T> write(
        commitMessage: String,
        mutate: () -> T,
    ): T =
        lock.withLock {
            try {
                val result = mutate()
                git.add().addFilepattern(".").call()
                git
                    .commit()
                    .setMessage(commitMessage)
                    .setAuthor("aide-kit", "aide-kit@local")
                    .call()
                if (!push()) {
                    if (!pull() || !push()) {
                        abortRebaseAndReset()
                        throw VaultConflictException()
                    }
                }
                result
            } catch (e: VaultConflictException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Vault write failed; resetting clone", e)
                abortRebaseAndReset()
                throw VaultConflictException()
            }
        }

    private fun push(): Boolean {
        val results = git.push().setCredentialsProvider(credentials).call()
        return results.all { pushResult ->
            pushResult.remoteUpdates.all {
                it.status == RemoteRefUpdate.Status.OK || it.status == RemoteRefUpdate.Status.UP_TO_DATE
            }
        }
    }

    private fun abortRebaseAndReset() {
        runCatching { git.rebase().setOperation(RebaseCommand.Operation.ABORT).call() }
        runCatching {
            git
                .reset()
                .setMode(ResetCommand.ResetType.HARD)
                .setRef("origin/${config.branch}")
                .call()
        }
    }
}
