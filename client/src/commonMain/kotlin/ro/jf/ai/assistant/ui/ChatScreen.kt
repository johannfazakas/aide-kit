package ro.jf.ai.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ro.jf.ai.assistant.presentation.ChatRole
import ro.jf.ai.assistant.presentation.ChatScreenModel
import ro.jf.ai.assistant.presentation.matchRanges

@Composable
fun ChatScreen(
    model: ChatScreenModel,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    highlightQuery: String = "",
) {
    val state by model.state.collectAsState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(state.transcript.size) {
        if (state.transcript.isNotEmpty()) listState.animateScrollToItem(state.transcript.size - 1)
    }

    Column(modifier.padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.transcript) { message ->
                val fromUser = message.role == ChatRole.USER
                Box(Modifier.fillMaxWidth()) {
                    Text(
                        highlighted(message.content, highlightQuery),
                        modifier =
                            Modifier
                                .align(if (fromUser) Alignment.CenterEnd else Alignment.CenterStart)
                                .background(
                                    if (fromUser) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    RoundedCornerShape(12.dp),
                                ).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val send = {
                if (input.isNotBlank() && !state.sending) {
                    model.send(input)
                    input = ""
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Ask about your tasks…") },
                modifier = Modifier.weight(1f).submitOnEnter(send),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            Button(
                onClick = send,
                enabled = input.isNotBlank() && !state.sending,
            ) { Text(if (state.sending) "…" else "Send") }
        }
    }
}

@Composable
private fun highlighted(
    content: String,
    query: String,
): AnnotatedString {
    val style = SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer)
    return buildAnnotatedString {
        append(content)
        matchRanges(content, query).forEach { range ->
            addStyle(style, range.first, range.last + 1)
        }
    }
}
