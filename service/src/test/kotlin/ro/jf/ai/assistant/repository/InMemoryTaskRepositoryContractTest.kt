package ro.jf.ai.assistant.repository

import ro.jf.ai.assistant.service.TaskService

class InMemoryTaskRepositoryContractTest : TaskRepositoryContractTest() {
    override fun newService(): TaskService = TaskService(InMemoryTaskRepository(topics = listOf("home", "health")))
}
