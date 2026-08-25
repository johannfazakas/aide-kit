package ro.jf.ai.assistant.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import ro.jf.ai.assistant.transfer.ErrorResponse

class ApiException(
    val status: Int,
    message: String,
) : Exception(message)

fun apiHttpClient(): HttpClient =
    HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }

internal suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
    if (!status.isSuccess()) throw toApiException()
    return body()
}

internal suspend fun HttpResponse.toApiException(): ApiException {
    val message =
        try {
            body<ErrorResponse>().message
        } catch (_: Exception) {
            status.toString()
        }
    return ApiException(status.value, message)
}
