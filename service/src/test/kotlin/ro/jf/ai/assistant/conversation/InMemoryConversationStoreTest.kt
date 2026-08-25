package ro.jf.ai.assistant.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryConversationStoreTest {
    @Test
    fun `given no requested id when resolving a session then a new id is minted`() {
        val ids = ArrayDeque(listOf("1", "2"))
        val store = InMemoryConversationStore(idGenerator = ids::removeFirst)

        val first = store.resolveSessionId(null)
        val second = store.resolveSessionId(null)

        assertEquals("1", first)
        assertEquals("2", second)
    }

    @Test
    fun `given a known session id when resolving then the same id is returned`() {
        val store = InMemoryConversationStore()
        store.append("known", ConversationMessage(ConversationRole.USER, "hello"))

        assertEquals("known", store.resolveSessionId("known"))
    }

    @Test
    fun `given an unknown session id when resolving then a new id is minted instead`() {
        val store = InMemoryConversationStore(idGenerator = { "minted" })

        assertEquals("minted", store.resolveSessionId("forged"))
    }

    @Test
    fun `given an unknown session when history requested then it is empty`() {
        val store = InMemoryConversationStore()

        assertEquals(emptyList(), store.history("missing"))
    }

    @Test
    fun `given appended messages when history requested then they are returned in order`() {
        val store = InMemoryConversationStore()

        store.append("s1", ConversationMessage(ConversationRole.USER, "hello"))
        store.append("s1", ConversationMessage(ConversationRole.ASSISTANT, "hi"))

        assertEquals(
            listOf(
                ConversationMessage(ConversationRole.USER, "hello"),
                ConversationMessage(ConversationRole.ASSISTANT, "hi"),
            ),
            store.history("s1"),
        )
    }

    @Test
    fun `given two sessions when messages appended then histories are isolated`() {
        val store = InMemoryConversationStore()

        store.append("s1", ConversationMessage(ConversationRole.USER, "in one"))
        store.append("s2", ConversationMessage(ConversationRole.USER, "in two"))

        assertEquals(listOf(ConversationMessage(ConversationRole.USER, "in one")), store.history("s1"))
        assertEquals(listOf(ConversationMessage(ConversationRole.USER, "in two")), store.history("s2"))
    }

    @Test
    fun `given more messages than the window when appended then only the most recent are kept`() {
        val store = InMemoryConversationStore(maxMessages = 3)

        repeat(5) { index ->
            store.append("s1", ConversationMessage(ConversationRole.USER, "m$index"))
        }

        assertEquals(
            listOf(
                ConversationMessage(ConversationRole.USER, "m2"),
                ConversationMessage(ConversationRole.USER, "m3"),
                ConversationMessage(ConversationRole.USER, "m4"),
            ),
            store.history("s1"),
        )
    }

    @Test
    fun `given multiple messages in one append when appended then all are stored`() {
        val store = InMemoryConversationStore()

        store.append(
            "s1",
            ConversationMessage(ConversationRole.USER, "ask"),
            ConversationMessage(ConversationRole.ASSISTANT, "answer"),
        )

        assertEquals(2, store.history("s1").size)
        assertTrue(store.history("s1").any { it.content == "answer" })
    }
}
