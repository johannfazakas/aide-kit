package ro.jf.ai.assistant.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import ro.jf.ai.assistant.client.TasksApiClient
import ro.jf.ai.assistant.transfer.CreateTaskRequest
import ro.jf.ai.assistant.transfer.TaskResponse
import ro.jf.ai.assistant.transfer.UpdateTaskRequest

data class TasksState(
    val tasks: List<TaskResponse> = emptyList(),
    val filter: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val pendingDelete: TaskResponse? = null,
) {
    val visibleTasks: List<TaskResponse>
        get() =
            if (filter.isBlank()) {
                tasks
            } else {
                tasks.filter { it.title.contains(filter, ignoreCase = true) }
            }
}

class TasksScreenModel(
    private val client: TasksApiClient,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(TasksState())
    val state: StateFlow<TasksState> = mutableState.asStateFlow()

    private var mutationCount = 0
    private val updatingTaskIds = mutableSetOf<String>()

    fun refresh() {
        mutableState.update { it.copy(loading = true, error = null) }
        val mutationsAtStart = mutationCount
        scope.launch {
            runCatching { client.listTasks() }
                .onSuccess { tasks ->
                    mutableState.update {
                        if (mutationCount == mutationsAtStart) {
                            it.copy(tasks = tasks, loading = false)
                        } else {
                            it.copy(loading = false)
                        }
                    }
                }.onFailure { failWith(it) }
        }
    }

    fun setFilter(text: String) {
        mutableState.update { it.copy(filter = text) }
    }

    fun create(
        title: String,
        dueDate: LocalDate? = null,
        category: String? = null,
    ) {
        mutableState.update { it.copy(error = null) }
        scope.launch {
            runCatching { client.createTask(CreateTaskRequest(title = title, dueDate = dueDate, category = category)) }
                .onSuccess { created ->
                    mutationCount++
                    mutableState.update { it.copy(tasks = it.tasks + created) }
                }.onFailure { failWith(it) }
        }
    }

    fun update(
        task: TaskResponse,
        title: String = task.title,
        dueDate: LocalDate? = task.dueDate,
        category: String? = task.category,
        completed: Boolean = task.completed,
    ) {
        if (!updatingTaskIds.add(task.id)) return
        mutableState.update { it.copy(error = null) }
        scope.launch {
            runCatching {
                client.updateTask(
                    task.id,
                    UpdateTaskRequest(title = title, dueDate = dueDate, category = category, completed = completed),
                )
            }.onSuccess { updated ->
                mutationCount++
                mutableState.update { state ->
                    state.copy(tasks = state.tasks.map { if (it.id == updated.id) updated else it })
                }
            }.onFailure { failWith(it) }
            updatingTaskIds.remove(task.id)
        }
    }

    fun toggleCompleted(task: TaskResponse) = update(task, completed = !task.completed)

    fun requestDelete(task: TaskResponse) {
        mutableState.update { it.copy(pendingDelete = task) }
    }

    fun cancelDelete() {
        mutableState.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val target = mutableState.value.pendingDelete ?: return
        mutableState.update { it.copy(pendingDelete = null, error = null) }
        scope.launch {
            runCatching { client.deleteTask(target.id) }
                .onSuccess {
                    mutationCount++
                    mutableState.update { state -> state.copy(tasks = state.tasks.filterNot { it.id == target.id }) }
                }.onFailure { failWith(it) }
        }
    }

    private fun failWith(cause: Throwable) {
        mutableState.update { it.copy(loading = false, error = cause.toUserMessage()) }
    }
}
