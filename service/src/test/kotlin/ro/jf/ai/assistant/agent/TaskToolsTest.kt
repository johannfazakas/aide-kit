package ro.jf.ai.assistant.agent

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ro.jf.ai.assistant.repository.InMemoryTaskRepository
import ro.jf.ai.assistant.service.TaskService
import ro.jf.ai.assistant.transfer.CreateTaskRequest
import ro.jf.ai.assistant.transfer.TaskResponse
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
        assertEquals("[]", tools.listTasks(topic = null))
    }

    @Test
    fun `given stored tasks when listTasks then returns all as json`() {
        service.create(CreateTaskRequest(title = "First"))
        service.create(CreateTaskRequest(title = "Second"))

        val tasks = json.decodeFromString<List<TaskResponse>>(tools.listTasks(topic = null))

        assertEquals(setOf("First", "Second"), tasks.map { it.title }.toSet())
    }

    @Test
    fun `given tasks in topics when listTasks with topic then returns only matching`() {
        service.create(CreateTaskRequest(title = "Dentist", topic = "health"))
        service.create(CreateTaskRequest(title = "Rent", topic = "home"))

        val tasks = json.decodeFromString<List<TaskResponse>>(tools.listTasks(topic = "health"))

        assertEquals(listOf("Dentist"), tasks.map { it.title })
    }

    @Test
    fun `given seeded topics when listTopics then returns them as json`() {
        val seededTools = TaskTools(TaskService(InMemoryTaskRepository(topics = listOf("alpha", "beta"))))

        assertEquals(listOf("alpha", "beta"), json.decodeFromString<List<String>>(seededTools.listTopics()))
    }

    @Test
    fun `given an unknown topic when createTask then returns error and stores nothing`() {
        val error = errorOf(tools.createTask(title = "Task", dueDate = null, topic = "nonsense"))

        assertTrue(error!!.contains("nonsense"))
        assertTrue(repository.findAll().isEmpty())
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
                tools.createTask(title = "Dentist", dueDate = "2026-08-10", topic = "health"),
            )

        assertEquals("Dentist", task.title)
        assertEquals(LocalDate.parse("2026-08-10"), task.dueDate)
        assertEquals("health", task.topic)
        assertFalse(task.done)
        assertEquals("Dentist", repository.findById(task.id)?.title)
    }

    @Test
    fun `given a blank title when createTask then returns error`() {
        val error = errorOf(tools.createTask(title = "   ", dueDate = null, topic = null))

        assertEquals("Title must not be blank", error)
    }

    @Test
    fun `given an invalid date when createTask then returns date format error`() {
        val error = errorOf(tools.createTask(title = "Dentist", dueDate = "next week", topic = null))

        assertTrue(error!!.contains("ISO-8601"))
        assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `given a stored task when updateTask with done then task is done`() {
        val created = service.create(CreateTaskRequest(title = "Dentist", topic = "health"))

        val task =
            json.decodeFromString<TaskResponse>(
                tools.updateTask(
                    id = created.id,
                    title = "Dentist",
                    dueDate = null,
                    topic = "health",
                    done = true,
                ),
            )

        assertTrue(task.done)
        assertEquals(true, repository.findById(created.id)?.done)
    }

    @Test
    fun `given a stored task when updateTask omitting fields then fields are cleared`() {
        val created =
            service.create(
                CreateTaskRequest(title = "Dentist", dueDate = LocalDate.parse("2026-08-10"), topic = "health"),
            )

        val task =
            json.decodeFromString<TaskResponse>(
                tools.updateTask(id = created.id, title = "Dentist"),
            )

        assertNull(task.dueDate)
        assertNull(task.topic)
    }

    @Test
    fun `given an unknown id when updateTask then returns not found error`() {
        val error = errorOf(tools.updateTask(id = "missing", title = "Dentist"))

        assertTrue(error!!.contains("missing"))
    }
}
