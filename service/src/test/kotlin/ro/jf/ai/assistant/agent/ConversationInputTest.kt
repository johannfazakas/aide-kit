package ro.jf.ai.assistant.agent

import ro.jf.ai.assistant.conversation.ConversationMessage
import ro.jf.ai.assistant.conversation.ConversationRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationInputTest {
    @Test
    fun `given no history when building input then the message is returned unchanged`() {
        val input = buildAgentInput(emptyList(), "add a task")

        assertEquals("add a task", input)
    }

    @Test
    fun `given prior turns when building input then they precede the current message as a transcript`() {
        val history =
            listOf(
                ConversationMessage(ConversationRole.USER, "add dentist"),
                ConversationMessage(ConversationRole.ASSISTANT, "Created dentist (#a1)"),
            )

        val input = buildAgentInput(history, "mark it done")

        assertEquals(
            """
            Conversation so far:
            User: add dentist
            Assistant: Created dentist (#a1)

            User: mark it done
            """.trimIndent(),
            input,
        )
    }

    @Test
    fun `given history when building input then the current message appears last`() {
        val history = listOf(ConversationMessage(ConversationRole.USER, "earlier"))

        val input = buildAgentInput(history, "latest")

        assertTrue(input.trimEnd().endsWith("User: latest"))
    }
}
