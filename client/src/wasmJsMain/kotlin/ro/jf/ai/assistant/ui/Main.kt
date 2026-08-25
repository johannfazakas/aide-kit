package ro.jf.ai.assistant.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window

private const val SERVICE_PORT = 7080

private fun serviceBaseUrl(): String = "http://${window.location.hostname}:$SERVICE_PORT"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        App(baseUrl = serviceBaseUrl())
    }
}
