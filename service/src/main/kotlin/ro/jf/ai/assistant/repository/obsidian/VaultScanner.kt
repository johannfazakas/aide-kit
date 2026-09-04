package ro.jf.ai.assistant.repository.obsidian

import kotlinx.datetime.LocalDate
import org.slf4j.LoggerFactory
import ro.jf.ai.assistant.model.Task

const val DEFAULT_INBOX_PATH = "organization/Inbox.md"
const val DEFAULT_REGISTRY_PATH = "organization/Topics.md"

data class VaultFile(
    val relativePath: String,
    val content: String,
)

data class ScannedTask(
    val task: Task,
    val explicitId: Boolean,
)

data class VaultScan(
    val tasks: List<ScannedTask>,
    val topics: List<String>,
    val topicToFile: Map<String, String>,
) {
    fun findById(id: String): Task? =
        tasks.firstOrNull { it.explicitId && it.task.id == id }?.task
            ?: tasks.firstOrNull { it.task.id == id }?.task
}

class VaultScanner(
    private val inboxPath: String = DEFAULT_INBOX_PATH,
    val registryPath: String = DEFAULT_REGISTRY_PATH,
) {
    private val logger = LoggerFactory.getLogger(VaultScanner::class.java)

    private val checkboxRegex = Regex("""^(\s*)[-*+] \[( |x|X|-)]\s+(.*)$""")
    private val fieldRegex = Regex("""\[([A-Za-z_][\w-]*)::\s*([^]]*)]""")

    fun scan(files: List<VaultFile>): VaultScan {
        val topics = readTopics(files)
        val topicToFile = mutableMapOf<String, String>()
        val parsedByFile = mutableListOf<Pair<VaultFile, List<ParsedTask>>>()
        val explicitIds = mutableSetOf<String>()
        var withoutExplicitId = 0

        for (file in files) {
            val fileTopic = parseFrontmatter(file.content)["topic"]?.let { scalarValue(it.firstOrNull()) }
            val isTaskFile = fileTopic != null || file.relativePath == inboxPath
            if (!isTaskFile) continue
            if (fileTopic != null) topicToFile.putIfAbsent(fileTopic, file.relativePath)

            val tasks = parseTasks(file, fileTopic).filter { it.recurrence == null || it.dueRaw != null }
            parsedByFile += file to tasks
            for (parsed in tasks) {
                if (parsed.explicitId != null) explicitIds += parsed.explicitId else withoutExplicitId++
            }
        }

        val scanned = mutableListOf<ScannedTask>()
        for ((file, tasks) in parsedByFile) {
            val occurrences = mutableMapOf<String, Int>()
            for (parsed in tasks) {
                val signature = parsed.signature()
                val occurrence = occurrences.getOrElse(signature) { 0 }
                occurrences[signature] = occurrence + 1
                val id = parsed.explicitId ?: deriveId(file.relativePath, parsed, occurrence)
                if (parsed.explicitId == null && id in explicitIds) {
                    logger.warn("Derived id {} collides with an explicit id in {}", id, file.relativePath)
                }
                scanned +=
                    ScannedTask(
                        task =
                            Task(
                                id = id,
                                title = parsed.title,
                                dueDate = parsed.dueDate,
                                topic = parsed.effectiveTopic,
                                done = parsed.done,
                            ),
                        explicitId = parsed.explicitId != null,
                    )
            }
        }

        if (withoutExplicitId > 0) {
            logger.info("Scanned {} task(s) without an explicit id", withoutExplicitId)
        }
        return VaultScan(scanned, topics, topicToFile)
    }

    fun readTopics(files: List<VaultFile>): List<String> =
        files.firstOrNull { it.relativePath == registryPath }?.let { parseTopics(it.content) }.orEmpty()

    fun parseTopics(registryContent: String): List<String> =
        parseFrontmatter(registryContent)["topics"].orEmpty().mapNotNull { scalarValue(it) }

    private fun parseTasks(
        file: VaultFile,
        fileTopic: String?,
    ): List<ParsedTask> {
        val lines = file.content.lines()
        val parsed = mutableListOf<ParsedTask>()
        var i = 0
        while (i < lines.size) {
            val match = checkboxRegex.find(lines[i])
            if (match == null) {
                i++
                continue
            }
            val done = match.groupValues[2] != " "
            val title = stripEmphasis(match.groupValues[3])
            val fields = mutableMapOf<String, String>()
            i++
            while (i < lines.size && isFieldLine(lines[i])) {
                fieldRegex.findAll(lines[i]).forEach { field ->
                    fields.putIfAbsent(field.groupValues[1].lowercase(), field.groupValues[2].trim())
                }
                i++
            }
            val inlineTopic = fields["topic"]
            parsed +=
                ParsedTask(
                    title = title,
                    done = done,
                    dueRaw = fields["due"],
                    dueDate = fields["due"]?.let { parseDue(it) },
                    effectiveTopic = inlineTopic ?: fileTopic,
                    recurrence = fields["recurrence"],
                    explicitId = fields["id"]?.takeIf { it.isNotBlank() },
                )
        }
        return parsed
    }

    private fun isFieldLine(line: String): Boolean =
        line.isNotBlank() && line.first().isWhitespace() && fieldRegex.containsMatchIn(line)

    private fun deriveId(
        relativePath: String,
        parsed: ParsedTask,
        occurrence: Int,
    ): String =
        TaskIdentity.token(
            listOf(
                relativePath.replace('\\', '/'),
                parsed.title,
                parsed.dueRaw.orEmpty(),
                parsed.effectiveTopic.orEmpty(),
                parsed.recurrence.orEmpty(),
                occurrence.toString(),
            ).joinToString("\n"),
        )

    private fun parseDue(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.trim().substringBefore(' ')) }.getOrNull()

    private fun stripEmphasis(raw: String): String {
        val trimmed = raw.trim()
        for (marker in listOf("***", "**", "*", "__", "_")) {
            if (trimmed.length >= marker.length * 2 && trimmed.startsWith(marker) && trimmed.endsWith(marker)) {
                val inner = trimmed.substring(marker.length, trimmed.length - marker.length)
                if (!inner.contains(marker)) return inner.trim()
            }
        }
        return trimmed
    }

    private fun parseFrontmatter(content: String): Map<String, List<String>> {
        val lines = content.lines()
        if (lines.firstOrNull()?.trim() != "---") return emptyMap()
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (end < 0) return emptyMap()
        val body = lines.subList(1, end + 1)
        val result = mutableMapOf<String, List<String>>()
        var i = 0
        while (i < body.size) {
            val line = body[i]
            val keyMatch = Regex("""^([A-Za-z_][\w-]*):\s*(.*)$""").find(line)
            if (keyMatch == null) {
                i++
                continue
            }
            val key = keyMatch.groupValues[1]
            val inline = keyMatch.groupValues[2].trim()
            when {
                inline.startsWith("[") && inline.endsWith("]") -> {
                    result[key] =
                        inline
                            .removeSurrounding("[", "]")
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    i++
                }

                inline.isNotEmpty() -> {
                    result[key] = listOf(inline)
                    i++
                }

                else -> {
                    val items = mutableListOf<String>()
                    i++
                    while (i < body.size && body[i].trimStart().startsWith("- ")) {
                        items += body[i].trimStart().removePrefix("- ").trim()
                        i++
                    }
                    result[key] = items
                }
            }
        }
        return result
    }

    private fun scalarValue(raw: String?): String? =
        raw
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private data class ParsedTask(
        val title: String,
        val done: Boolean,
        val dueRaw: String?,
        val dueDate: LocalDate?,
        val effectiveTopic: String?,
        val recurrence: String?,
        val explicitId: String?,
    ) {
        fun signature(): String =
            listOf(title, dueRaw.orEmpty(), effectiveTopic.orEmpty(), recurrence.orEmpty()).joinToString(" ")
    }
}
