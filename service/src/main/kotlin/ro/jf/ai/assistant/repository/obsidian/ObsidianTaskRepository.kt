package ro.jf.ai.assistant.repository.obsidian

import kotlinx.datetime.LocalDate
import ro.jf.ai.assistant.exception.UnsupportedTaskOperationException
import ro.jf.ai.assistant.model.Task
import ro.jf.ai.assistant.repository.TaskRepository
import java.io.File
import java.util.UUID

class ObsidianTaskRepository(
    private val bridge: VaultGitBridge,
    private val scanner: VaultScanner,
    private val inboxPath: String = DEFAULT_INBOX_PATH,
    private val idGenerator: () -> String = { TaskIdentity.token(UUID.randomUUID().toString()) },
) : TaskRepository {
    override fun create(
        title: String,
        dueDate: LocalDate?,
        topic: String?,
        done: Boolean,
    ): Task =
        bridge.write("agent: add task \"$title\"") {
            bridge.pull()
            val scan = scanner.scan(bridge.markdownFiles())
            val id = generateId(scan.tasks.map { it.task.id }.toSet())
            val target = resolveTarget(topic, scan)
            val block = formatBlock(title, dueDate, id, topic, target.inheritsTopic, done)
            appendUnderTasksHeading(File(bridge.root, target.path), block)
            Task(id, title, dueDate, topic, done)
        }

    override fun findAll(topic: String?): List<Task> =
        bridge.read {
            scanner
                .scan(bridge.markdownFiles())
                .tasks
                .map { it.task }
                .filter { topic == null || it.topic == topic }
        }

    override fun findById(id: String): Task? = bridge.read { scanner.scan(bridge.markdownFiles()).findById(id) }

    override fun listTopics(): List<String> =
        bridge.read {
            val registry = File(bridge.root, scanner.registryPath)
            if (registry.isFile) scanner.parseTopics(registry.readText()) else emptyList()
        }

    override fun update(
        id: String,
        title: String,
        dueDate: LocalDate?,
        topic: String?,
        done: Boolean,
    ): Task = throw UnsupportedTaskOperationException("Updating tasks")

    override fun delete(id: String): Boolean = throw UnsupportedTaskOperationException("Deleting tasks")

    private data class Target(
        val path: String,
        val inheritsTopic: Boolean,
    )

    private fun resolveTarget(
        topic: String?,
        scan: VaultScan,
    ): Target {
        if (topic == null) return Target(inboxPath, inheritsTopic = false)
        val topicFile = scan.topicToFile[topic]
        return if (topicFile != null) {
            Target(topicFile, inheritsTopic = true)
        } else {
            Target(inboxPath, inheritsTopic = false)
        }
    }

    private fun generateId(existing: Set<String>): String {
        repeat(100) {
            val id = idGenerator()
            if (id !in existing) return id
        }
        error("Could not generate a unique task id")
    }

    private fun formatBlock(
        title: String,
        dueDate: LocalDate?,
        id: String,
        topic: String?,
        inheritsTopic: Boolean,
        done: Boolean,
    ): String =
        buildString {
            append("- [${if (done) "x" else " "}] **$title**\n")
            if (dueDate != null) append("      [due:: $dueDate]\n")
            append("      [id:: $id]\n")
            if (topic != null && !inheritsTopic) append("      [topic:: $topic]\n")
        }

    private fun appendUnderTasksHeading(
        file: File,
        block: String,
    ) {
        val existing = if (file.isFile) file.readText() else ""
        val result =
            if (Regex("(?m)^## Tasks[ \\t]*$").containsMatchIn(existing)) {
                insertIntoTasksSection(existing, block)
            } else if (existing.isEmpty()) {
                "## Tasks\n\n$block"
            } else {
                "${existing.trimEnd('\n')}\n\n## Tasks\n\n$block"
            }
        file.parentFile?.mkdirs()
        file.writeText(result)
    }

    private fun insertIntoTasksSection(
        content: String,
        block: String,
    ): String {
        val heading = Regex("(?m)^## Tasks[ \\t]*$").find(content)!!
        val nextHeading = Regex("(?m)^## ").find(content, heading.range.last + 1)
        val insertAt = nextHeading?.range?.first ?: content.length
        val before = "${content.substring(0, insertAt).trimEnd('\n')}\n\n"
        val after = content.substring(insertAt)
        return if (after.isEmpty()) "$before$block" else "$before$block\n$after"
    }
}
