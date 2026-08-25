package ro.jf.ai.assistant.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

fun testApiHttpClient(handler: MockRequestHandler): HttpClient =
    HttpClient(MockEngine(handler)) {
        install(ContentNegotiation) {
            json()
        }
    }

fun jsonHeaders(): Headers = headersOf(HttpHeaders.ContentType, "application/json")
