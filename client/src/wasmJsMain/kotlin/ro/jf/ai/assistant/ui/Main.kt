package ro.jf.ai.assistant.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import ro.jf.ai.assistant.client.apiHttpClient
import ro.jf.ai.assistant.presentation.DEFAULT_SERVICE_PORT
import ro.jf.ai.assistant.presentation.createScreenModels

private fun serviceBaseUrl(): String = "http://${window.location.hostname}:$DEFAULT_SERVICE_PORT"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        val scope = rememberCoroutineScope()
        val models = remember { createScreenModels(apiHttpClient(), serviceBaseUrl(), scope) }
        App(models.tasks, models.chat)
    }
}
