package ro.jf.ai.assistant.presentation

import ro.jf.ai.assistant.client.ApiException

internal fun Throwable.toUserMessage(): String =
    if (this is ApiException) message ?: "Request failed" else "Service unreachable"
