package ro.jf.ai.assistant.repository.obsidian

import kotlinx.datetime.LocalDate
import ro.jf.ai.assistant.exception.UnsupportedTaskOperationException
import ro.jf.ai.assistant.model.Task
import ro.jf.ai.assistant.repository.TaskPatch
import ro.jf.ai.assistant.repository.TaskRepository
import java.io.File
import java.util.UUID

class ObsidianTaskRepository(
    private val bridge: VaultGitBridge,
    private val scanner: VaultScanner,
    private val inboxPath: String = DEFAULT_INBOX_PATH,
    private val idGenerator: () -> String = { TaskIdentity.token(UUID.randomUUID().toString()) },
    private val editor: TaskBlockEditor = TaskBlockEditor(),
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
            requireKnownTopic(topic, scan.topics.toSet())
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

    override fun findById(id: String): Task? =
        bridge.read {
            scanner.scan(bridge.markdownFiles()).findById(id)
        }

    override fun listTopics(): List<String> =
        bridge.read {
            val registry = File(bridge.root, scanner.registryPath)
            if (registry.isFile) scanner.parseTopics(registry.readText()) else emptyList()
        }

    override fun update(
        id: String,
        patch: TaskPatch,
    ): Task? =
        bridge.write<Task?>("agent: update task") {
            bridge.pull()
            val files = bridge.markdownFiles()
            val scan = scanner.scan(files)
            val scanned = scan.findScannedById(id) ?: return@write null
            val current = scanned.task
            requireKnownTopic(patch.topic?.value, scan.topics.toSet())
            val target = patch.applyTo(current)

            val sourceRelativePath = scanned.location.relativePath
            val sourceFile = File(bridge.root, sourceRelativePath)
            val raw = files.first { it.relativePath == sourceRelativePath }.content
            val terminator = dominantTerminator(raw)
            val lines = raw.lines().toMutableList()
            val block =
                lines.subList(scanned.location.startLine, scanned.location.endLine).toMutableList()

            editor.normalizeCheckboxLine(block, current, patch)
            if (patch.done != null && target.done != current.done) editor.setDone(block, target.done)
            if (patch.dueDate != null) editor.setDue(block, target.dueDate)
            if (!scanned.explicitId) editor.stampId(block, current.id)

            val topicChanges = patch.topic != null && target.topic != current.topic
            val relocation = if (topicChanges) resolveTarget(target.topic, scan) else null
            if (relocation != null && relocation.path != sourceRelativePath) {
                editor.setInlineTopic(block, if (relocation.inheritsTopic) null else target.topic)
                removeBlock(lines, scanned.location.startLine, scanned.location.endLine)
                sourceFile.writeText(lines.joinToString(terminator))
                appendUnderTasksHeading(File(bridge.root, relocation.path), block)
            } else {
                if (topicChanges) editor.setInlineTopic(block, target.topic)
                replaceBlock(lines, scanned.location.startLine, scanned.location.endLine, block)
                sourceFile.writeText(lines.joinToString(terminator))
            }
            target
        }

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

    private fun dominantTerminator(raw: String): String {
        val crlf = Regex("\r\n").findAll(raw).count()
        val lf = raw.count { it == '\n' } - crlf
        return if (crlf > lf) "\r\n" else "\n"
    }

    private fun requireKnownTopic(
        topic: String?,
        known: Set<String>,
    ) {
        if (topic != null && topic !in known) {
            throw IllegalArgumentException(
                "Unknown topic '$topic'; choose one of ${known.toList()} or omit the topic",
            )
        }
    }

    private fun formatBlock(
        title: String,
        dueDate: LocalDate?,
        id: String,
        topic: String?,
        inheritsTopic: Boolean,
        done: Boolean,
    ): List<String> =
        buildList {
            add("- [${if (done) "x" else " "}] ${VaultTaskGrammar.boldTitle(title)}")
            if (dueDate != null) add(fieldLine("due", dueDate.toString()))
            add(fieldLine("id", id))
            if (topic != null && !inheritsTopic) add(fieldLine("topic", topic))
        }

    private fun fieldLine(
        key: String,
        value: String,
    ): String = "${VaultTaskGrammar.FIELD_INDENT}${VaultTaskGrammar.field(key, value)}"

    private fun appendUnderTasksHeading(
        file: File,
        blockLines: List<String>,
    ) {
        val existing = if (file.isFile) file.readText() else ""
        val terminator = if (existing.isNotEmpty()) dominantTerminator(existing) else "\n"
        val block = blockLines.joinToString(terminator) + terminator
        val result =
            if (VaultTaskGrammar.tasksHeading.containsMatchIn(existing)) {
                insertIntoTasksSection(existing, block, terminator)
            } else if (existing.isEmpty()) {
                "## Tasks$terminator$terminator$block"
            } else {
                "${existing.trimEnd('\n', '\r')}$terminator$terminator## Tasks$terminator$terminator$block"
            }
        file.parentFile?.mkdirs()
        file.writeText(result)
    }

    private fun removeBlock(
        lines: MutableList<String>,
        start: Int,
        end: Int,
    ) {
        repeat(end - start) { lines.removeAt(start) }
        if (start > 0 && lines.getOrNull(start - 1)?.isBlank() == true && lines.getOrNull(start)?.isBlank() == true) {
            lines.removeAt(start)
        }
    }

    private fun replaceBlock(
        lines: MutableList<String>,
        start: Int,
        end: Int,
        block: List<String>,
    ) {
        repeat(end - start) { lines.removeAt(start) }
        lines.addAll(start, block)
    }

    private fun insertIntoTasksSection(
        content: String,
        block: String,
        terminator: String,
    ): String {
        val heading = VaultTaskGrammar.tasksHeading.find(content)!!
        val nextHeading = Regex("""(?m)^#{1,2}[ \t]""").find(content, heading.range.last + 1)
        val insertAt = nextHeading?.range?.first ?: content.length
        val before = "${content.substring(0, insertAt).trimEnd('\n', '\r')}$terminator$terminator"
        val after = content.substring(insertAt)
        return if (after.isEmpty()) "$before$block" else "$before$block$terminator$after"
    }
}
