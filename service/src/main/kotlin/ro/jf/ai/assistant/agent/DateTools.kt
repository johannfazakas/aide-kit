package ro.jf.ai.assistant.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

@LLMDescription("Tools for date awareness")
class DateTools(
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ToolSet {
    private val json = Json

    @Tool
    @LLMDescription(
        "Get the current date in ISO-8601 format (yyyy-MM-dd) and the current day of week. " +
            "Call this to resolve relative date expressions like 'tomorrow' or 'next Friday' " +
            "into concrete dates.",
    )
    fun currentDate(): String {
        val today = clock.todayIn(timeZone)
        return json.encodeToString(CurrentDate(today.toString(), today.dayOfWeek.name))
    }

    @Serializable
    private data class CurrentDate(
        val date: String,
        val dayOfWeek: String,
    )
}
