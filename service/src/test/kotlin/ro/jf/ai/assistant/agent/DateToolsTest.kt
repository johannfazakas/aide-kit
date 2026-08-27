package ro.jf.ai.assistant.agent

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class DateToolsTest {
    private fun fixedClock(instant: String): Clock =
        object : Clock {
            override fun now(): Instant = Instant.parse(instant)
        }

    @Test
    fun `given fixed clock when currentDate then returns iso date and day of week`() {
        val tools = DateTools(clock = fixedClock("2026-08-26T12:00:00Z"), timeZone = TimeZone.UTC)

        assertEquals("""{"date":"2026-08-26","dayOfWeek":"WEDNESDAY"}""", tools.currentDate())
    }

    @Test
    fun `given instant before midnight in zone ahead of utc when currentDate then returns next day`() {
        val tools = DateTools(clock = fixedClock("2026-08-26T22:30:00Z"), timeZone = TimeZone.of("Europe/Bucharest"))

        assertEquals("""{"date":"2026-08-27","dayOfWeek":"THURSDAY"}""", tools.currentDate())
    }

    @Test
    fun `given same instant in utc when currentDate then returns previous day relative to zone ahead`() {
        val tools = DateTools(clock = fixedClock("2026-08-26T22:30:00Z"), timeZone = TimeZone.UTC)

        assertEquals("""{"date":"2026-08-26","dayOfWeek":"WEDNESDAY"}""", tools.currentDate())
    }
}
