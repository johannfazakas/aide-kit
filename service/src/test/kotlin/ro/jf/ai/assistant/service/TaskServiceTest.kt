package ro.jf.ai.assistant.service

import kotlinx.datetime.LocalDate
import ro.jf.ai.assistant.exception.TaskNotFoundException
import ro.jf.ai.assistant.repository.InMemoryTaskRepository
import ro.jf.ai.assistant.transfer.CreateTaskRequest
import ro.jf.ai.assistant.transfer.UpdateTaskRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskServiceTest {
    private var nextId = 0
    private val repository = InMemoryTaskRepository(idGenerator = { "id-${nextId++}" })
    private val service = TaskService(repository)

    @Test
    fun `given a title only when create then task is stored with defaults`() {
        val task = service.create(CreateTaskRequest(title = "Pay rent"))

        assertEquals("id-0", task.id)
        assertEquals("Pay rent", task.title)
        assertNull(task.dueDate)
        assertNull(task.topic)
        assertFalse(task.done)
        assertEquals(task, repository.findById(task.id))
    }

    @Test
    fun `given all fields when create then task is stored with given values`() {
        val task =
            service
                .create(
                    CreateTaskRequest(title = "Dentist", dueDate = LocalDate.parse("2026-08-10"), topic = "health"),
                )

        assertEquals(LocalDate.parse("2026-08-10"), task.dueDate)
        assertEquals("health", task.topic)
    }

    @Test
    fun `given a blank title when create then throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { service.create(CreateTaskRequest(title = "   ")) }
    }

    @Test
    fun `given a title with a line break when create then throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            service.create(CreateTaskRequest(title = "line one\nline two"))
        }
        assertTrue(service.list().isEmpty())
    }

    @Test
    fun `given a title with a line break when rename then throws IllegalArgumentException`() {
        val task = service.create(CreateTaskRequest(title = "Valid"))

        assertFailsWith<IllegalArgumentException> { service.rename(task.id, "bad\rtitle") }
    }

    @Test
    fun `given an unknown topic when create then throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            service.create(CreateTaskRequest(title = "Task", topic = "nonsense"))
        }
    }

    @Test
    fun `given seeded topics when listTopics then returns the repository topics`() {
        val seededService = TaskService(InMemoryTaskRepository(topics = listOf("alpha", "beta")))

        assertEquals(listOf("alpha", "beta"), seededService.listTopics())
    }

    @Test
    fun `given stored tasks when list then returns all`() {
        val first = service.create(CreateTaskRequest(title = "First"))
        val second = service.create(CreateTaskRequest(title = "Second"))

        assertEquals(setOf(first, second), service.list().toSet())
    }

    @Test
    fun `given tasks in topics when list by topic then returns only matching`() {
        val work = service.create(CreateTaskRequest(title = "Report", topic = "work"))
        service.create(CreateTaskRequest(title = "Dishes", topic = "home"))

        assertEquals(listOf(work), service.list(topic = "work"))
    }

    @Test
    fun `given an existing task when get then returns it`() {
        val task = service.create(CreateTaskRequest(title = "Read book"))

        assertEquals(task, service.get(task.id))
    }

    @Test
    fun `given an unknown id when get then throws TaskNotFoundException`() {
        assertFailsWith<TaskNotFoundException> { service.get("missing") }
    }

    @Test
    fun `given an existing task when update then replaces all fields and preserves id`() {
        val task =
            service
                .create(
                    CreateTaskRequest(title = "Old", dueDate = LocalDate.parse("2026-07-31"), topic = "home"),
                )

        val updated = service.update(task.id, UpdateTaskRequest(title = "New", done = true))

        assertEquals(task.id, updated.id)
        assertEquals("New", updated.title)
        assertNull(updated.dueDate)
        assertNull(updated.topic)
        assertTrue(updated.done)
    }

    @Test
    fun `given an unknown id when update then throws TaskNotFoundException`() {
        assertFailsWith<TaskNotFoundException> { service.update("missing", UpdateTaskRequest(title = "Title")) }
    }

    @Test
    fun `given a blank title when update then throws IllegalArgumentException`() {
        val task = service.create(CreateTaskRequest(title = "Valid"))

        assertFailsWith<IllegalArgumentException> { service.update(task.id, UpdateTaskRequest(title = " ")) }
    }

    @Test
    fun `given an open task when complete then only done changes`() {
        val task =
            service
                .create(
                    CreateTaskRequest(title = "Dentist", dueDate = LocalDate.parse("2026-09-10"), topic = "health"),
                )

        val updated = service.complete(task.id)

        assertEquals(task.copy(done = true), updated)
    }

    @Test
    fun `given a done task when reopen then only done changes`() {
        val task = service.create(CreateTaskRequest(title = "Dentist", topic = "health", done = true))

        val updated = service.reopen(task.id)

        assertEquals(task.copy(done = false), updated)
    }

    @Test
    fun `given a task when reschedule then only the due date changes`() {
        val task =
            service.create(CreateTaskRequest(title = "Dentist", dueDate = LocalDate.parse("2026-09-10")))

        val updated = service.reschedule(task.id, LocalDate.parse("2026-09-20"))

        assertEquals(task.copy(dueDate = LocalDate.parse("2026-09-20")), updated)
    }

    @Test
    fun `given a task with a due date when reschedule with null then the due date is cleared`() {
        val task =
            service.create(CreateTaskRequest(title = "Dentist", dueDate = LocalDate.parse("2026-09-10")))

        val updated = service.reschedule(task.id, null)

        assertNull(updated.dueDate)
        assertEquals(task.copy(dueDate = null), updated)
    }

    @Test
    fun `given a task when rename then only the title changes`() {
        val task = service.create(CreateTaskRequest(title = "Old", topic = "home"))

        val updated = service.rename(task.id, "New")

        assertEquals(task.copy(title = "New"), updated)
    }

    @Test
    fun `given a blank title when rename then throws IllegalArgumentException`() {
        val task = service.create(CreateTaskRequest(title = "Valid"))

        assertFailsWith<IllegalArgumentException> { service.rename(task.id, "  ") }
    }

    @Test
    fun `given a task when changeTopic then only the topic changes`() {
        val task = service.create(CreateTaskRequest(title = "Dentist", topic = "home"))

        val updated = service.changeTopic(task.id, "health")

        assertEquals(task.copy(topic = "health"), updated)
    }

    @Test
    fun `given an unknown topic when changeTopic then throws IllegalArgumentException`() {
        val task = service.create(CreateTaskRequest(title = "Dentist", topic = "home"))

        assertFailsWith<IllegalArgumentException> { service.changeTopic(task.id, "nonsense") }
        assertEquals("home", repository.findById(task.id)?.topic)
    }

    @Test
    fun `given an unknown id when complete then throws TaskNotFoundException`() {
        assertFailsWith<TaskNotFoundException> { service.complete("missing") }
    }

    @Test
    fun `given an unknown id when reschedule then throws TaskNotFoundException`() {
        assertFailsWith<TaskNotFoundException> { service.reschedule("missing", null) }
    }

    @Test
    fun `given an existing task when delete then it is removed`() {
        val task = service.create(CreateTaskRequest(title = "Doomed"))

        service.delete(task.id)

        assertNull(repository.findById(task.id))
    }

    @Test
    fun `given an unknown id when delete then throws TaskNotFoundException`() {
        assertFailsWith<TaskNotFoundException> { service.delete("missing") }
    }
}
