package ro.jf.ai.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import ro.jf.ai.assistant.presentation.TasksScreenModel
import ro.jf.ai.assistant.transfer.TaskResponse

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(trim()) }.getOrNull()

private fun String.isValidDateInput(): Boolean = isBlank() || toLocalDateOrNull() != null

@Composable
fun TasksScreen(
    model: TasksScreenModel,
    modifier: Modifier = Modifier,
    filterFocus: FocusRequester = remember { FocusRequester() },
) {
    val state by model.state.collectAsState()
    var editing by remember(model) { mutableStateOf<TaskResponse?>(null) }

    Column(modifier.padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.filter,
                onValueChange = model::setFilter,
                label = { Text("Filter") },
                modifier = Modifier.weight(1f).focusRequester(filterFocus),
                singleLine = true,
            )
            Button(onClick = model::refresh) { Text("Refresh") }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        CreateTaskForm(onCreate = { title, due, category -> model.create(title, due, category) })
        HorizontalDivider()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.visibleTasks, key = { it.id }) { task ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = task.completed, onCheckedChange = { model.toggleCompleted(task) })
                    Column(Modifier.weight(1f)) {
                        Text(task.title, style = MaterialTheme.typography.bodyLarge)
                        val details = listOfNotNull(task.dueDate?.toString(), task.category).joinToString(" · ")
                        if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { editing = task }) { Text("Edit") }
                    TextButton(onClick = { model.requestDelete(task) }) { Text("Delete") }
                }
            }
        }
    }

    state.pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = model::cancelDelete,
            title = { Text("Delete task?") },
            text = { Text(task.title) },
            confirmButton = { TextButton(onClick = model::confirmDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = model::cancelDelete) { Text("Cancel") } },
        )
    }

    editing?.let { task ->
        EditTaskDialog(
            task = task,
            onSave = { title, due, category ->
                model.update(task, title = title, dueDate = due, category = category)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CreateTaskForm(onCreate: (String, LocalDate?, String?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    val submit = {
        if (title.isNotBlank() && due.isValidDateInput()) {
            onCreate(title.trim(), due.toLocalDateOrNull(), category.trim().ifBlank { null })
            title = ""
            due = ""
            category = ""
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("New task") },
            modifier = Modifier.weight(2f).submitOnEnter(submit),
            singleLine = true,
        )
        OutlinedTextField(
            value = due,
            onValueChange = { due = it },
            label = { Text("Due (yyyy-mm-dd)") },
            modifier = Modifier.weight(1f).submitOnEnter(submit),
            singleLine = true,
            isError = !due.isValidDateInput(),
        )
        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Category") },
            modifier = Modifier.weight(1f).submitOnEnter(submit),
            singleLine = true,
        )
        Button(
            onClick = submit,
            enabled = title.isNotBlank() && due.isValidDateInput(),
        ) { Text("Add") }
    }
}

@Composable
private fun EditTaskDialog(
    task: TaskResponse,
    onSave: (String, LocalDate?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(task.title) }
    var due by remember { mutableStateOf(task.dueDate?.toString() ?: "") }
    var category by remember { mutableStateOf(task.category ?: "") }
    val submit = {
        if (title.isNotBlank() && due.isValidDateInput()) {
            onSave(title.trim(), due.toLocalDateOrNull(), category.trim().ifBlank { null })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.submitOnEnter(submit),
                )
                OutlinedTextField(
                    value = due,
                    onValueChange = { due = it },
                    label = { Text("Due (yyyy-mm-dd)") },
                    modifier = Modifier.submitOnEnter(submit),
                    isError = !due.isValidDateInput(),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    modifier = Modifier.submitOnEnter(submit),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = submit,
                enabled = title.isNotBlank() && due.isValidDateInput(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
