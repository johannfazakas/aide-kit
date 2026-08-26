package ro.jf.ai.assistant.presentation

data class ChatMatch(
    val messageIndex: Int,
    val range: IntRange,
)

fun matchRanges(
    text: String,
    query: String,
): List<IntRange> {
    if (query.isBlank()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var index = text.indexOf(query, ignoreCase = true)
    while (index >= 0) {
        ranges += index until index + query.length
        index = text.indexOf(query, index + query.length, ignoreCase = true)
    }
    return ranges
}

fun findChatMatches(
    messages: List<String>,
    query: String,
): List<ChatMatch> =
    messages.flatMapIndexed { messageIndex, message ->
        matchRanges(message, query).map { ChatMatch(messageIndex, it) }
    }

fun wrappedNext(
    current: Int,
    size: Int,
): Int = if (size == 0) 0 else (current + 1) % size

fun wrappedPrevious(
    current: Int,
    size: Int,
): Int = if (size == 0) 0 else (current - 1 + size) % size
