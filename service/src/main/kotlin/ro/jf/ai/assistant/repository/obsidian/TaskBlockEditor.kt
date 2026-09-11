package ro.jf.ai.assistant.repository.obsidian

import kotlinx.datetime.LocalDate
import ro.jf.ai.assistant.model.Task
import ro.jf.ai.assistant.repository.TaskPatch

class TaskBlockEditor {
    fun normalizeCheckboxLine(
        block: MutableList<String>,
        current: Task,
        patch: TaskPatch,
    ) {
        val match =
            VaultTaskGrammar.checkboxLine.matchEntire(block[0])
                ?: error("Checkbox line no longer matches the task grammar: ${block[0]}")
        val rest = match.groupValues[4]
        val titleChanges = patch.title != null && patch.title.value != current.title
        val inlineFields = VaultTaskGrammar.inlineField.findAll(rest).toList()
        val leaving = inlineFields.filter { titleChanges || patch.names(it.groupValues[1].lowercase()) }
        if (!titleChanges && leaving.isEmpty()) return

        val newRest =
            if (titleChanges) {
                VaultTaskGrammar.boldTitle(patch.title!!.value)
            } else {
                stripFields(rest, leaving)
            }
        block[0] = match.groupValues[1] + match.groupValues[2] + match.groupValues[3] + newRest

        val indent = fieldIndent(block)
        var insertAt = 1
        for (field in leaving) {
            if (!patch.names(field.groupValues[1].lowercase())) {
                block.add(insertAt++, "$indent${field.value}")
            }
        }
    }

    fun setDone(
        block: MutableList<String>,
        done: Boolean,
    ) {
        val match =
            VaultTaskGrammar.checkboxLine.matchEntire(block[0])
                ?: error("Checkbox line no longer matches the task grammar: ${block[0]}")
        val state = if (done) "x" else " "
        block[0] = match.groupValues[1] + state + match.groupValues[3] + match.groupValues[4]
    }

    fun setDue(
        block: MutableList<String>,
        dueDate: LocalDate?,
    ) = setField(block, "due", dueDate?.toString(), insertIndex = 1)

    fun setInlineTopic(
        block: MutableList<String>,
        topic: String?,
    ) = setField(block, "topic", topic, insertIndex = block.size)

    private fun setField(
        block: MutableList<String>,
        key: String,
        value: String?,
        insertIndex: Int,
    ) {
        val regex = VaultTaskGrammar.fieldRegex(key)
        val index = fieldLineIndex(block, regex)
        when {
            value == null -> if (index != null) removeField(block, index, regex)
            index != null -> block[index] = replaceField(block[index], regex, VaultTaskGrammar.field(key, value))
            else -> block.add(insertIndex, "${fieldIndent(block)}${VaultTaskGrammar.field(key, value)}")
        }
    }

    fun stampId(
        block: MutableList<String>,
        id: String,
    ) {
        val dueIndex = fieldLineIndex(block, VaultTaskGrammar.fieldRegex("due"))
        block.add((dueIndex ?: 0) + 1, "${fieldIndent(block)}${VaultTaskGrammar.field("id", id)}")
    }

    private fun TaskPatch.names(key: String): Boolean =
        when (key) {
            "due" -> dueDate != null
            "topic" -> topic != null
            else -> false
        }

    private fun stripFields(
        rest: String,
        leaving: List<MatchResult>,
    ): String {
        var result = rest
        for (field in leaving.sortedByDescending { it.range.first }) {
            result = result.removeRange(field.range)
        }
        return result.trim()
    }

    private fun replaceField(
        line: String,
        regex: Regex,
        replacement: String,
    ): String = regex.replaceFirst(line, Regex.escapeReplacement(replacement))

    private fun fieldLineIndex(
        block: List<String>,
        regex: Regex,
    ): Int? = (1 until block.size).firstOrNull { regex.containsMatchIn(block[it]) }

    private fun removeField(
        block: MutableList<String>,
        index: Int,
        regex: Regex,
    ) {
        val stripped = regex.replaceFirst(block[index], "")
        if (stripped.isBlank()) block.removeAt(index) else block[index] = stripped.trimEnd()
    }

    private fun fieldIndent(block: List<String>): String =
        block
            .drop(1)
            .firstOrNull()
            ?.takeWhile { it.isWhitespace() }
            ?.takeIf { it.isNotEmpty() }
            ?: VaultTaskGrammar.FIELD_INDENT
}
