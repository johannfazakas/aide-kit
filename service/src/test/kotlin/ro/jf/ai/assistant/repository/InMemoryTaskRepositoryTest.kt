package ro.jf.ai.assistant.repository

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryTaskRepositoryTest {
    private var nextId = 0
    private val repository = InMemoryTaskRepository(idGenerator = { "id-${nextId++}" })

    @Test
    fun `given a new task when create then assigns generated id and stores it`() {
        val task = repository.create("Pay rent", LocalDate.parse("2026-07-31"), "home", false)

        assertEquals("id-0", task.id)
        assertEquals("Pay rent", task.title)
        assertEquals(LocalDate.parse("2026-07-31"), task.dueDate)
        assertEquals("home", task.topic)
        assertFalse(task.done)
        assertEquals(task, repository.findById("id-0"))
    }

    @Test
    fun `given multiple tasks when findAll then returns all of them`() {
        val first = repository.create("First", null, null, false)
        val second = repository.create("Second", null, null, false)

        val all = repository.findAll()

        assertEquals(setOf(first, second), all.toSet())
    }

    @Test
    fun `given no tasks when findAll then returns empty list`() {
        assertEquals(emptyList(), repository.findAll())
    }

    @Test
    fun `given tasks in different topics when findAll by topic then returns only exact matches`() {
        val work = repository.create("Report", null, "work", false)
        repository.create("Dishes", null, "home", false)
        repository.create("Untopiced", null, null, false)

        assertEquals(listOf(work), repository.findAll(topic = "work"))
    }

    @Test
    fun `given seeded topics when listTopics then returns them`() {
        val seeded = InMemoryTaskRepository(topics = listOf("alpha", "beta"))

        assertEquals(listOf("alpha", "beta"), seeded.listTopics())
    }

    @Test
    fun `given an unknown id when findById then returns null`() {
        assertNull(repository.findById("missing"))
    }

    @Test
    fun `given an existing task when update then replaces fields and preserves id`() {
        val task = repository.create("Old title", LocalDate.parse("2026-07-31"), "home", false)

        val updated = repository.update(task.id, "New title", null, null, true)

        assertEquals(task.id, updated?.id)
        assertEquals("New title", updated?.title)
        assertNull(updated?.dueDate)
        assertNull(updated?.topic)
        assertTrue(updated?.done == true)
        assertEquals(updated, repository.findById(task.id))
    }

    @Test
    fun `given an unknown id when update then returns null`() {
        assertNull(repository.update("missing", "Title", null, null, false))
    }

    @Test
    fun `given an existing task when delete then removes it`() {
        val task = repository.create("Doomed", null, null, false)

        assertTrue(repository.delete(task.id))
        assertNull(repository.findById(task.id))
        assertEquals(emptyList(), repository.findAll())
    }

    @Test
    fun `given an unknown id when delete then returns false`() {
        assertFalse(repository.delete("missing"))
    }
}
