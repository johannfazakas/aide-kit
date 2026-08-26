package ro.jf.ai.assistant.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ro.jf.ai.assistant.presentation.ChatScreenModel
import ro.jf.ai.assistant.presentation.TasksScreenModel

private enum class Destination(
    val label: String,
    val symbol: String,
) {
    TASKS("Tasks", "☑"),
    CHAT("Chat", "💬"),
}

@Composable
fun App(
    tasksModel: TasksScreenModel,
    chatModel: ChatScreenModel,
    topBar: @Composable () -> Unit = {},
) {
    var destination by rememberSaveable { mutableStateOf(Destination.TASKS) }

    LaunchedEffect(destination, tasksModel) {
        if (destination == Destination.TASKS) tasksModel.refresh()
    }

    MaterialTheme {
        Scaffold(
            topBar = topBar,
            bottomBar = {
                NavigationBar {
                    Destination.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = destination == entry,
                            onClick = { destination = entry },
                            icon = { Text(entry.symbol) },
                            label = { Text(entry.label) },
                        )
                    }
                }
            },
        ) { padding ->
            when (destination) {
                Destination.TASKS -> TasksScreen(tasksModel, Modifier.padding(padding))
                Destination.CHAT -> ChatScreen(chatModel, Modifier.padding(padding))
            }
        }
    }
}
