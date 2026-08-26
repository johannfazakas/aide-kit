package ro.jf.ai.assistant.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import ro.jf.ai.assistant.presentation.ChatScreenModel
import ro.jf.ai.assistant.presentation.TasksScreenModel
import ro.jf.ai.assistant.ui.theme.AppIcons
import ro.jf.ai.assistant.ui.theme.AppTheme

enum class Destination(
    val label: String,
    val icon: ImageVector,
) {
    TASKS("Tasks", AppIcons.Tasks),
    CHAT("Chat", AppIcons.Chat),
}

@Composable
fun App(
    tasksModel: TasksScreenModel,
    chatModel: ChatScreenModel,
    topBar: @Composable () -> Unit = {},
    chatListState: LazyListState = rememberLazyListState(),
    chatHighlightQuery: String = "",
    tasksFilterFocus: FocusRequester = remember { FocusRequester() },
    onDestinationChange: (Destination) -> Unit = {},
) {
    var destination by rememberSaveable { mutableStateOf(Destination.TASKS) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(destination, tasksModel) {
        onDestinationChange(destination)
        if (destination == Destination.TASKS) tasksModel.refresh()
    }

    AppTheme {
        BoxWithConstraints {
            val wideLayout = maxWidth >= 840.dp
            Scaffold(
                modifier =
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) {
                            focusManager.moveFocus(
                                if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next,
                            )
                            true
                        } else {
                            false
                        }
                    },
                topBar = topBar,
                bottomBar = {
                    if (!wideLayout) {
                        NavigationBar {
                            Destination.entries.forEach { entry ->
                                NavigationBarItem(
                                    selected = destination == entry,
                                    onClick = { destination = entry },
                                    icon = { Icon(entry.icon, contentDescription = entry.label) },
                                    label = { Text(entry.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Row(Modifier.padding(padding)) {
                    if (wideLayout) {
                        NavigationRail {
                            Destination.entries.forEach { entry ->
                                NavigationRailItem(
                                    selected = destination == entry,
                                    onClick = { destination = entry },
                                    icon = { Icon(entry.icon, contentDescription = entry.label) },
                                    label = { Text(entry.label) },
                                )
                            }
                        }
                    }
                    val screenModifier = Modifier.weight(1f)
                    when (destination) {
                        Destination.TASKS -> {
                            TasksScreen(tasksModel, screenModifier, filterFocus = tasksFilterFocus)
                        }

                        Destination.CHAT -> {
                            ChatScreen(
                                chatModel,
                                screenModifier,
                                listState = chatListState,
                                highlightQuery = chatHighlightQuery,
                            )
                        }
                    }
                }
            }
        }
    }
}
