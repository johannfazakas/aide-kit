package ro.jf.ai.assistant.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import ro.jf.ai.assistant.client.apiHttpClient
import ro.jf.ai.assistant.presentation.DEFAULT_SERVICE_PORT
import ro.jf.ai.assistant.presentation.ScreenModels
import ro.jf.ai.assistant.presentation.createScreenModels

private fun serviceBaseUrl(): String = "http://${window.location.hostname}:$DEFAULT_SERVICE_PORT"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        val scope = rememberCoroutineScope()
        val models = remember { createScreenModels(apiHttpClient(), serviceBaseUrl(), scope) }
        WebApp(models)
    }
}

@Composable
private fun WebApp(models: ScreenModels) {
    var destination by remember { mutableStateOf(Destination.TASKS) }
    var findOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()
    val tasksFilterFocus = remember { FocusRequester() }
    val findFieldFocus = remember { FocusRequester() }
    val closeFind = {
        findOpen = false
        query = ""
    }

    DisposableEffect(Unit) {
        val onKeyDown: (Event) -> Unit = { event ->
            val key = event as KeyboardEvent
            when {
                (key.ctrlKey || key.metaKey) && key.key.lowercase() == "f" -> {
                    event.preventDefault()
                    when (destination) {
                        Destination.TASKS -> tasksFilterFocus.requestFocus()
                        Destination.CHAT -> findOpen = true
                    }
                }

                key.key == "Tab" -> {
                    event.preventDefault()
                }

                key.key == "Escape" && findOpen -> {
                    closeFind()
                }
            }
        }
        document.addEventListener("keydown", onKeyDown)
        onDispose { document.removeEventListener("keydown", onKeyDown) }
    }

    LaunchedEffect(findOpen) {
        if (findOpen) findFieldFocus.requestFocus()
    }
    LaunchedEffect(destination) {
        if (destination != Destination.CHAT) closeFind()
    }

    App(
        models.tasks,
        models.chat,
        topBar = {
            if (findOpen) {
                FindBar(
                    chatModel = models.chat,
                    query = query,
                    onQueryChange = { query = it },
                    onClose = closeFind,
                    chatListState = chatListState,
                    fieldFocus = findFieldFocus,
                )
            }
        },
        chatListState = chatListState,
        chatHighlightQuery = if (findOpen) query else "",
        tasksFilterFocus = tasksFilterFocus,
        onDestinationChange = { destination = it },
    )
}
