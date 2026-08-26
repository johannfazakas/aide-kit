package ro.jf.ai.assistant.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatMarkdownTest {
    @Test
    fun `given bold notation when rendering then markers are stripped and the span covers the content`() {
        val rendered = renderMarkdown("- **Creating** new tasks")

        assertEquals("• Creating new tasks", rendered.text)
        assertEquals(listOf(StyleSpan(MarkdownStyle.BOLD, 2..9)), rendered.spans)
    }

    @Test
    fun `given italic and code notation when rendering then spans align with the display text`() {
        val rendered = renderMarkdown("an *important* word, run `ls` now")

        assertEquals("an important word, run ls now", rendered.text)
        assertEquals(
            listOf(
                StyleSpan(MarkdownStyle.ITALIC, 3..11),
                StyleSpan(MarkdownStyle.CODE, 23..24),
            ),
            rendered.spans,
        )
    }

    @Test
    fun `given a heading line when rendering then the marker is stripped and the line is styled`() {
        val rendered = renderMarkdown("# Your tasks")

        assertEquals("Your tasks", rendered.text)
        assertEquals(listOf(StyleSpan(MarkdownStyle.HEADING, 0..9)), rendered.spans)
    }

    @Test
    fun `given a multiline reply when rendering then offsets account for earlier lines`() {
        val rendered = renderMarkdown("Hello!\n\n- **Creating** new tasks")

        assertEquals("Hello!\n\n• Creating new tasks", rendered.text)
        assertEquals(listOf(StyleSpan(MarkdownStyle.BOLD, 10..17)), rendered.spans)
    }

    @Test
    fun `given star bullets with indentation when rendering then bullets are converted in place`() {
        val rendered = renderMarkdown("* first\n  * nested")

        assertEquals("• first\n  • nested", rendered.text)
        assertEquals(emptyList(), rendered.spans)
    }

    @Test
    fun `given unclosed or spaced markers when rendering then they stay literal`() {
        assertEquals(RenderedMessage("broken ** bold", emptyList()), renderMarkdown("broken ** bold"))
        assertEquals(RenderedMessage("2 * 3 * 4", emptyList()), renderMarkdown("2 * 3 * 4"))
    }

    @Test
    fun `given text without notation when rendering then it is unchanged with no spans`() {
        assertEquals(RenderedMessage("plain text", emptyList()), renderMarkdown("plain text"))
    }

    @Test
    fun `given a user message when rendering then the content stays exactly as typed`() {
        val rendered = ChatMessage(ChatRole.USER, "keep my **stars**").rendered()

        assertEquals(RenderedMessage("keep my **stars**", emptyList()), rendered)
    }

    @Test
    fun `given an assistant message when rendering then markdown is applied`() {
        val rendered = ChatMessage(ChatRole.ASSISTANT, "**done**").rendered()

        assertEquals(RenderedMessage("done", listOf(StyleSpan(MarkdownStyle.BOLD, 0..3))), rendered)
    }
}
