package ro.jf.ai.assistant.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ro.jf.ai.assistant.repository.InMemoryTaskRepository
import ro.jf.ai.assistant.service.TaskService
import ro.jf.ai.assistant.transfer.CreateTaskRequest
import ro.jf.ai.assistant.transfer.TaskResponse
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskToolsTest {
    private var nextId = 0
    private val repository = InMemoryTaskRepository(idGenerator = { "id-${nextId++}" })
    private val service = TaskService(repository)
    private val tools = TaskTools(service)
    private val json = Json

    private fun errorOf(result: String): String? =
        json
            .parseToJsonElement(result)
            .jsonObject["error"]
            ?.jsonPrimitive
            ?.content

    @Test
    fun `given no tasks when listTasks then returns empty json array`() {
        assertEquals("[]", tools.listTasks(category = null))
    }

    @Test
    fun `given stored tasks when listTasks then returns all as json`() {
        service.create(CreateTaskRequest(title = "First"))
        service.create(CreateTaskRequest(title = "Second"))

        val tasks = json.decodeFromString<List<TaskResponse>>(tools.listTasks(category = null))

        assertEquals(setOf("First", "Second"), tasks.map { it.title }.toSet())
    }

    @Test
    fun `given tasks in categories when listTasks with category then returns only matching`() {
        service.create(CreateTaskRequest(title = "Dentist", category = "health"))
        service.create(CreateTaskRequest(title = "Rent", category = "home"))

        val tasks = json.decodeFromString<List<TaskResponse>>(tools.listTasks(category = "health"))

        assertEquals(listOf("Dentist"), tasks.map { it.title })
    }

    @Test
    fun `given a stored task when getTask then returns it as json`() {
        val created = service.create(CreateTaskRequest(title = "Dentist"))

        val task = json.decodeFromString<TaskResponse>(tools.getTask(created.id))

        assertEquals(created.id, task.id)
        assertEquals("Dentist", task.title)
    }

    @Test
    fun `given an unknown id when getTask then returns not found error`() {
        val error = errorOf(tools.getTask("missing"))

        assertTrue(error!!.contains("missing"))
    }

    @Test
    fun `given title and fields when createTask then task is stored and returned`() {
        val task =
            json.decodeFromString<TaskResponse>(
                tools.createTask(title = "Dentist", dueDate = "2026-08-10", category = "health"),
            )

        assertEquals("Dentist", task.title)
        assertEquals(LocalDate.parse("2026-08-10"), task.dueDate)
        assertEquals("health", task.category)
        assertFalse(task.completed)
        assertEquals("Dentist", repository.findById(task.id)?.title)
    }

    @Test
    fun `given a blank title when createTask then returns error`() {
        val error = errorOf(tools.createTask(title = "   ", dueDate = null, category = null))

        assertEquals("Title must not be blank", error)
    }

    @Test
    fun `given an invalid date when createTask then returns date format error`() {
        val error = errorOf(tools.createTask(title = "Dentist", dueDate = "next week", category = null))

        assertTrue(error!!.contains("ISO-8601"))
        assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `given a stored task when updateTask with completed then task is completed`() {
        val created = service.create(CreateTaskRequest(title = "Dentist", category = "health"))

        val task =
            json.decodeFromString<TaskResponse>(
                tools.updateTask(
                    id = created.id,
                    title = "Dentist",
                    dueDate = null,
                    category = "health",
                    completed = true,
                ),
            )

        assertTrue(task.completed)
        assertEquals(true, repository.findById(created.id)?.completed)
    }

    @Test
    fun `given a stored task when updateTask omitting fields then fields are cleared`() {
        val created =
            service.create(
                CreateTaskRequest(title = "Dentist", dueDate = LocalDate.parse("2026-08-10"), category = "health"),
            )

        val task =
            json.decodeFromString<TaskResponse>(
                tools.updateTask(id = created.id, title = "Dentist"),
            )

        assertNull(task.dueDate)
        assertNull(task.category)
    }

    @Test
    fun `given an unknown id when updateTask then returns not found error`() {
        val error = errorOf(tools.updateTask(id = "missing", title = "Dentist"))

        assertTrue(error!!.contains("missing"))
    }
}
