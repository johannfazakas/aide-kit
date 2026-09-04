package ro.jf.ai.assistant.routes

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import ro.jf.ai.assistant.config.StartupConfig
import ro.jf.ai.assistant.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CorsIntegrationTest {
    @Test
    fun `given no cors configuration when a localhost origin calls then cors is granted`() =
        testApplication {
            application { module(StartupConfig(openCodeApiKey = "test-key", corsAllowedOrigins = null)) }

            val response =
                client.get("/api/v1/tasks") {
                    header(HttpHeaders.Origin, "http://localhost:7081")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("http://localhost:7081", response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun `given no cors configuration when a loopback ip origin calls then cors is granted`() =
        testApplication {
            application { module(StartupConfig(openCodeApiKey = "test-key", corsAllowedOrigins = null)) }

            val response =
                client.get("/api/v1/tasks") {
                    header(HttpHeaders.Origin, "http://127.0.0.1:7081")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("http://127.0.0.1:7081", response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun `given a configured origin with trailing slash when the browser origin calls then cors is granted`() =
        testApplication {
            application {
                module(StartupConfig(openCodeApiKey = "test-key", corsAllowedOrigins = "https://App.example.com/"))
            }

            val response =
                client.get("/api/v1/tasks") {
                    header(HttpHeaders.Origin, "https://app.example.com")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("https://app.example.com", response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun `given configured origins when a listed origin calls then cors is granted`() =
        testApplication {
            application {
                module(StartupConfig(openCodeApiKey = "test-key", corsAllowedOrigins = "http://app.example.com"))
            }

            val response =
                client.get("/api/v1/tasks") {
                    header(HttpHeaders.Origin, "http://app.example.com")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("http://app.example.com", response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun `given configured origins when a foreign origin calls then cors is refused`() =
        testApplication {
            application {
                module(StartupConfig(openCodeApiKey = "test-key", corsAllowedOrigins = "http://app.example.com"))
            }

            val response =
                client.get("/api/v1/tasks") {
                    header(HttpHeaders.Origin, "http://evil.example.com")
                }

            assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun `given no cors configuration when a non-localhost origin calls then cors is refused`() =
        testApplication {
            application { module(StartupConfig(openCodeApiKey = "test-key", corsAllowedOrigins = null)) }

            val response =
                client.get("/api/v1/tasks") {
                    header(HttpHeaders.Origin, "http://app.example.com")
                }

            assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
        }
}
