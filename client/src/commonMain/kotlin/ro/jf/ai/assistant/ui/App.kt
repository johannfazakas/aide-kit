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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ro.jf.ai.assistant.client.AssistantApiClient
import ro.jf.ai.assistant.client.TasksApiClient
import ro.jf.ai.assistant.client.apiHttpClient
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
fun App(baseUrl: String) {
    val scope = rememberCoroutineScope()
    val httpClient = remember { apiHttpClient() }
    val tasksModel = remember { TasksScreenModel(TasksApiClient(httpClient, baseUrl), scope) }
    val chatModel = remember { ChatScreenModel(AssistantApiClient(httpClient, baseUrl), scope) }
    var destination by remember { mutableStateOf(Destination.TASKS) }

    LaunchedEffect(destination) {
        if (destination == Destination.TASKS) tasksModel.refresh()
    }

    MaterialTheme {
        Scaffold(
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
