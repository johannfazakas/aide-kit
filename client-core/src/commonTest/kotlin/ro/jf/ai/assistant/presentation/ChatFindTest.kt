package ro.jf.ai.assistant.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatFindTest {
    @Test
    fun `given text with mixed case occurrences when finding ranges then matches case insensitively in order`() {
        val ranges = matchRanges("Tea time: TEA or tea?", "tea")

        assertEquals(listOf(0..2, 10..12, 17..19), ranges)
    }

    @Test
    fun `given a blank query when finding ranges then returns no matches`() {
        assertEquals(emptyList(), matchRanges("anything", ""))
        assertEquals(emptyList(), matchRanges("anything", "   "))
    }

    @Test
    fun `given overlapping candidates when finding ranges then matches do not overlap`() {
        val ranges = matchRanges("aaaa", "aa")

        assertEquals(listOf(0..1, 2..3), ranges)
    }

    @Test
    fun `given a transcript when finding chat matches then returns message index and range per occurrence`() {
        val messages = listOf("buy milk", "milk it, MILK it", "nothing here")

        val matches = findChatMatches(messages, "milk")

        assertEquals(
            listOf(
                ChatMatch(0, 4..7),
                ChatMatch(1, 0..3),
                ChatMatch(1, 9..12),
            ),
            matches,
        )
    }

    @Test
    fun `given the last match when cycling next then wraps to the first`() {
        assertEquals(0, wrappedNext(2, 3))
        assertEquals(1, wrappedNext(0, 3))
    }

    @Test
    fun `given the first match when cycling previous then wraps to the last`() {
        assertEquals(2, wrappedPrevious(0, 3))
        assertEquals(0, wrappedPrevious(1, 3))
    }

    @Test
    fun `given no matches when cycling then stays at zero`() {
        assertEquals(0, wrappedNext(0, 0))
        assertEquals(0, wrappedPrevious(0, 0))
    }
}
