package ro.jf.ai.assistant.repository

import kotlinx.datetime.LocalDate
import ro.jf.ai.assistant.exception.TaskNotFoundException
import ro.jf.ai.assistant.model.Task
import ro.jf.ai.assistant.service.TaskService
import ro.jf.ai.assistant.transfer.CreateTaskRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

abstract class TaskRepositoryContractTest {
    protected abstract fun newService(): TaskService

    private val dueA = LocalDate.parse("2026-09-01")
    private val dueB = LocalDate.parse("2026-12-31")

    private fun TaskService.seed(): Task = create(CreateTaskRequest(title = "Task", dueDate = dueA, topic = "home"))

    @Test
    fun `given a complete patch when applied then only done changes`() {
        val service = newService()
        val created = service.seed()

        val updated = service.complete(created.id)

        assertEquals(created.copy(done = true), updated)
    }

    @Test
    fun `given a reschedule patch when applied then only the due date changes`() {
        val service = newService()
        val created = service.seed()

        val updated = service.reschedule(created.id, dueB)

        assertEquals(created.copy(dueDate = dueB), updated)
    }

    @Test
    fun `given a rename patch when applied then only the title changes`() {
        val service = newService()
        val created = service.seed()

        val updated = service.rename(created.id, "Renamed")

        assertEquals(created.copy(title = "Renamed"), updated)
    }

    @Test
    fun `given a change-topic patch when applied then only the topic changes`() {
        val service = newService()
        val created = service.seed()

        val updated = service.changeTopic(created.id, "health")

        assertEquals(created.copy(topic = "health"), updated)
    }

    @Test
    fun `given a null due patch when applied then the due date is cleared`() {
        val service = newService()
        val created = service.seed()

        val updated = service.reschedule(created.id, null)

        assertNull(updated.dueDate)
        assertEquals(created.copy(dueDate = null), updated)
    }

    @Test
    fun `given a concurrent change to an unnamed field when completing then that field survives`() {
        val service = newService()
        val created = service.seed()
        service.reschedule(created.id, dueB)

        val completed = service.complete(created.id)

        assertEquals(dueB, completed.dueDate)
        assertEquals(true, completed.done)
    }

    @Test
    fun `given an unknown id when updating then it fails with not found`() {
        val service = newService()

        assertFailsWith<TaskNotFoundException> { service.complete("missing0") }
    }

    @Test
    fun `given an unknown topic when changing topic then it fails`() {
        val service = newService()
        val created = service.seed()

        assertFailsWith<IllegalArgumentException> { service.changeTopic(created.id, "nonsense") }
    }
}
