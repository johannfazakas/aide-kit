package ro.jf.ai.assistant.repository.obsidian

import kotlinx.datetime.LocalDate
import ro.jf.ai.assistant.model.Task
import ro.jf.ai.assistant.repository.TaskPatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskBlockEditorTest {
    private val editor = TaskBlockEditor()

    private fun task(
        title: String,
        dueDate: LocalDate? = null,
        topic: String? = null,
        done: Boolean = false,
    ) = Task("id00000a", title, dueDate, topic, done)

    @Test
    fun `given a done flip only when editing then the inline field stays byte-identical`() {
        val block = mutableListOf("- [ ] Pay rent [due:: 2026-09-01]")
        val current = task("Pay rent", LocalDate.parse("2026-09-01"))

        editor.normalizeCheckboxLine(block, current, TaskPatch.complete())
        editor.setDone(block, true)

        assertEquals(listOf("- [x] Pay rent [due:: 2026-09-01]"), block)
    }

    @Test
    fun `given an inline due when rescheduling then it migrates to a follow-up line with no duplicate`() {
        val block = mutableListOf("- [ ] Pay rent [due:: 2026-09-01]")
        val current = task("Pay rent", LocalDate.parse("2026-09-01"))
        val patch = TaskPatch.reschedule(LocalDate.parse("2026-09-10"))

        editor.normalizeCheckboxLine(block, current, patch)
        editor.setDue(block, LocalDate.parse("2026-09-10"))

        assertEquals(listOf("- [ ] Pay rent", "      [due:: 2026-09-10]"), block)
    }

    @Test
    fun `given a title rewrite when editing then all checkbox-line inline fields migrate to follow-up lines`() {
        val block = mutableListOf("- [ ] Pay rent [due:: 2026-09-01] [topic:: home]")
        val current = task("Pay rent", LocalDate.parse("2026-09-01"), "home")

        editor.normalizeCheckboxLine(block, current, TaskPatch.rename("Rent"))

        assertEquals(
            listOf("- [ ] **Rent**", "      [due:: 2026-09-01]", "      [topic:: home]"),
            block,
        )
    }

    @Test
    fun `given an unparseable due when clearing then the due field line is removed`() {
        val block = mutableListOf("- [ ] Someday task", "      [due:: someday]")
        val current = task("Someday task")

        editor.normalizeCheckboxLine(block, current, TaskPatch.reschedule(null))
        editor.setDue(block, null)

        assertEquals(listOf("- [ ] Someday task"), block)
    }

    @Test
    fun `given a topic with regex metacharacters when setting the inline topic then it is written verbatim`() {
        val block = mutableListOf("- [ ] Save up", "      [topic:: old]")

        editor.setInlineTopic(block, "ca\$h")

        assertEquals(listOf("- [ ] Save up", "      [topic:: ca\$h]"), block)
    }

    @Test
    fun `given a line that no longer matches the grammar when editing then it fails loudly`() {
        val block = mutableListOf("not a checkbox line")

        assertFailsWith<IllegalStateException> { editor.setDone(block, true) }
    }
}
