package ro.jf.ai.assistant.repository.obsidian

import kotlinx.datetime.LocalDate
import ro.jf.ai.assistant.exception.UnsupportedTaskOperationException
import ro.jf.ai.assistant.model.Task
import ro.jf.ai.assistant.repository.TaskRepository
import java.io.File
import java.util.UUID

private const val DEFAULT_FIELD_INDENT = "      "

class ObsidianTaskRepository(
    private val bridge: VaultGitBridge,
    private val scanner: VaultScanner,
    private val inboxPath: String = DEFAULT_INBOX_PATH,
    private val idGenerator: () -> String = { TaskIdentity.token(UUID.randomUUID().toString()) },
) : TaskRepository {
    private val checkboxLineRegex = Regex("""^(\s*[-*+] \[)( |x|X|-)(]\s+)(.*)$""")

    private fun fieldRegexFor(key: String): Regex = Regex("""\[$key::\s*[^]]*]""")

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
    ): Task? {
        var message = "agent: update task \"$title\""
        return bridge.write({ message }) {
            bridge.pull()
            val scan = scanner.scan(bridge.markdownFiles())
            val scanned = scan.findScannedById(id) ?: return@write null
            val current = scanned.task
            message = updateMessage(current, title, dueDate, topic, done)

            val sourceFile = File(bridge.root, scanned.location.relativePath)
            val lines = sourceFile.readText().lines().toMutableList()
            val block = lines.subList(scanned.location.startLine, scanned.location.endLine).toMutableList()

            editCheckboxLine(block, current, title, done)
            editDueField(block, current.dueDate, dueDate)
            if (!scanned.explicitId) stampId(block, current.id)

            if (topic != current.topic && scanned.location.fileTopic != null) {
                val target = resolveTarget(topic, scan)
                editTopicField(block, if (target.inheritsTopic) null else topic)
                removeBlock(lines, scanned.location.startLine, scanned.location.endLine)
                sourceFile.writeText(lines.joinToString("\n"))
                appendUnderTasksHeading(File(bridge.root, target.path), block.joinToString("\n") + "\n")
            } else {
                if (topic != current.topic) editTopicField(block, topic)
                replaceBlock(lines, scanned.location.startLine, scanned.location.endLine, block)
                sourceFile.writeText(lines.joinToString("\n"))
            }
            Task(current.id, title, dueDate, topic, done)
        }
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

    private fun updateMessage(
        current: Task,
        title: String,
        dueDate: LocalDate?,
        topic: String?,
        done: Boolean,
    ): String {
        val changes =
            buildList {
                if (done != current.done) add(if (done) "complete" else "reopen")
                if (dueDate != current.dueDate) add("reschedule")
                if (title != current.title) add("rename")
                if (topic != current.topic) add("move")
            }
        return when (changes.singleOrNull()) {
            "complete" -> "agent: complete task \"${current.title}\""
            "reopen" -> "agent: reopen task \"${current.title}\""
            "reschedule" -> "agent: reschedule task \"$title\" to ${dueDate ?: "no due date"}"
            "rename" -> "agent: rename task \"${current.title}\" to \"$title\""
            "move" -> "agent: move task \"$title\" to ${topic?.let { "topic \"$it\"" } ?: "the inbox"}"
            else -> "agent: update task \"$title\""
        }
    }

    private fun editCheckboxLine(
        block: MutableList<String>,
        current: Task,
        title: String,
        done: Boolean,
    ) {
        val match = checkboxLineRegex.find(block[0]) ?: return
        val state = if (done != current.done) (if (done) "x" else " ") else match.groupValues[2]
        val rest = if (title != current.title) "**$title**" else match.groupValues[4]
        block[0] = match.groupValues[1] + state + match.groupValues[3] + rest
    }

    private fun editDueField(
        block: MutableList<String>,
        currentDue: LocalDate?,
        dueDate: LocalDate?,
    ) {
        if (dueDate == currentDue) return
        val regex = fieldRegexFor("due")
        val index = fieldLineIndex(block, regex)
        when {
            dueDate == null -> if (index != null) removeField(block, index, regex)
            index != null -> block[index] = regex.replace(block[index], "[due:: $dueDate]")
            else -> block.add(1, "${fieldIndent(block)}[due:: $dueDate]")
        }
    }

    private fun stampId(
        block: MutableList<String>,
        id: String,
    ) {
        val dueIndex = fieldLineIndex(block, fieldRegexFor("due"))
        block.add((dueIndex ?: 0) + 1, "${fieldIndent(block)}[id:: $id]")
    }

    private fun editTopicField(
        block: MutableList<String>,
        topic: String?,
    ) {
        val regex = fieldRegexFor("topic")
        val index = fieldLineIndex(block, regex)
        when {
            topic == null -> if (index != null) removeField(block, index, regex)
            index != null -> block[index] = regex.replace(block[index], "[topic:: $topic]")
            else -> block.add("${fieldIndent(block)}[topic:: $topic]")
        }
    }

    private fun fieldLineIndex(
        block: List<String>,
        regex: Regex,
    ): Int? = (1 until block.size).firstOrNull { regex.containsMatchIn(block[it]) }

    private fun removeField(
        block: MutableList<String>,
        index: Int,
        regex: Regex,
    ) {
        val stripped = regex.replace(block[index], "")
        if (stripped.isBlank()) block.removeAt(index) else block[index] = stripped.trimEnd()
    }

    private fun fieldIndent(block: List<String>): String =
        block
            .drop(1)
            .firstOrNull()
            ?.takeWhile { it.isWhitespace() }
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_FIELD_INDENT

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
    ): String {
        val heading = Regex("(?m)^## Tasks[ \\t]*$").find(content)!!
        val nextHeading = Regex("(?m)^## ").find(content, heading.range.last + 1)
        val insertAt = nextHeading?.range?.first ?: content.length
        val before = "${content.substring(0, insertAt).trimEnd('\n')}\n\n"
        val after = content.substring(insertAt)
        return if (after.isEmpty()) "$before$block" else "$before$block\n$after"
    }
}
