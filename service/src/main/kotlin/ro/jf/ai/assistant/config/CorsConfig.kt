package ro.jf.ai.assistant.config

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

private val loopbackHosts = setOf("localhost", "127.0.0.1", "[::1]")

fun Application.configureCors(allowedOrigins: String?) {
    val origins =
        allowedOrigins
            ?.split(",")
            ?.map { it.normalizedOrigin() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
    install(CORS) {
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        if (origins.isEmpty()) {
            allowOrigins { it.originHost() in loopbackHosts }
        } else {
            allowOrigins { it.normalizedOrigin() in origins }
        }
    }
}

private fun String.normalizedOrigin(): String = trim().removeSuffix("/").lowercase()

private fun String.originHost(): String {
    val hostAndPort = normalizedOrigin().substringAfter("://")
    return if (hostAndPort.startsWith("[")) {
        hostAndPort.substringBefore("]") + "]"
    } else {
        hostAndPort.substringBefore(":")
    }
}
