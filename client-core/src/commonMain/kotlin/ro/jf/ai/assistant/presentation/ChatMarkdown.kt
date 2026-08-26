package ro.jf.ai.assistant.presentation

enum class MarkdownStyle { BOLD, ITALIC, CODE, HEADING }

data class StyleSpan(
    val style: MarkdownStyle,
    val range: IntRange,
)

data class RenderedMessage(
    val text: String,
    val spans: List<StyleSpan>,
)

fun ChatMessage.rendered(): RenderedMessage =
    when (role) {
        ChatRole.ASSISTANT -> renderMarkdown(content)
        ChatRole.USER -> RenderedMessage(content, emptyList())
    }

fun renderMarkdown(source: String): RenderedMessage {
    val text = StringBuilder()
    val spans = mutableListOf<StyleSpan>()
    source.lines().forEachIndexed { index, line ->
        if (index > 0) text.append('\n')
        renderLine(line, text, spans)
    }
    return RenderedMessage(text.toString(), spans)
}

private val headingMarker = Regex("""^#{1,6}\s+""")
private val bulletMarker = Regex("""^(\s*)[-*]\s+""")

private fun renderLine(
    line: String,
    out: StringBuilder,
    spans: MutableList<StyleSpan>,
) {
    val heading = headingMarker.find(line)
    val bullet = bulletMarker.find(line)
    when {
        heading != null -> {
            val start = out.length
            renderInline(line.substring(heading.range.last + 1), out, spans)
            if (out.length > start) spans += StyleSpan(MarkdownStyle.HEADING, start until out.length)
        }

        bullet != null -> {
            out.append(bullet.groupValues[1]).append("• ")
            renderInline(line.substring(bullet.range.last + 1), out, spans)
        }

        else -> {
            renderInline(line, out, spans)
        }
    }
}

private fun renderInline(
    line: String,
    out: StringBuilder,
    spans: MutableList<StyleSpan>,
) {
    var i = 0
    while (i < line.length) {
        val marker =
            when {
                line.startsWith("**", i) -> "**" to MarkdownStyle.BOLD
                line[i] == '*' -> "*" to MarkdownStyle.ITALIC
                line[i] == '`' -> "`" to MarkdownStyle.CODE
                else -> null
            }
        if (marker == null) {
            out.append(line[i])
            i++
            continue
        }
        val (token, style) = marker
        val close = line.indexOf(token, i + token.length)
        val content = if (close > i) line.substring(i + token.length, close) else ""
        if (content.isNotEmpty() && !content.first().isWhitespace() && !content.last().isWhitespace()) {
            val start = out.length
            out.append(content)
            spans += StyleSpan(style, start until out.length)
            i = close + token.length
        } else {
            out.append(token)
            i += token.length
        }
    }
}
