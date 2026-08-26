package ro.jf.ai.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import ro.jf.ai.assistant.presentation.ChatScreenModel
import ro.jf.ai.assistant.presentation.findChatMatches
import ro.jf.ai.assistant.presentation.wrappedNext
import ro.jf.ai.assistant.presentation.wrappedPrevious

@Composable
internal fun FindBar(
    chatModel: ChatScreenModel,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    chatListState: LazyListState,
    fieldFocus: FocusRequester,
) {
    val state by chatModel.state.collectAsState()
    val matches = remember(query, state.transcript) { findChatMatches(state.transcript.map { it.content }, query) }
    var current by remember { mutableStateOf(0) }

    LaunchedEffect(matches) {
        if (current >= matches.size) current = 0
    }
    LaunchedEffect(matches, current) {
        matches.getOrNull(current)?.let { chatListState.animateScrollToItem(it.messageIndex) }
    }

    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Find in conversation") },
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(fieldFocus)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                current =
                                    if (event.isShiftPressed) {
                                        wrappedPrevious(current, matches.size)
                                    } else {
                                        wrappedNext(current, matches.size)
                                    }
                                true
                            } else {
                                false
                            }
                        },
                singleLine = true,
            )
            Text(if (matches.isEmpty()) "0/0" else "${current + 1}/${matches.size}")
            TextButton(onClick = { current = wrappedPrevious(current, matches.size) }, enabled = matches.isNotEmpty()) {
                Text("↑")
            }
            TextButton(onClick = { current = wrappedNext(current, matches.size) }, enabled = matches.isNotEmpty()) {
                Text("↓")
            }
            TextButton(onClick = onClose) { Text("✕") }
        }
    }
}
