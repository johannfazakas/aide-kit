package ro.jf.ai.assistant.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StartupConfigTest {
    @Test
    fun `given no profile value when loading then local profile with memory storage`() {
        val config = loadStartupConfig(null)

        assertEquals(AppProfile.LOCAL, config.profile)
        assertEquals("memory", config.taskStorage)
    }

    @Test
    fun `given a blank profile value when loading then it defaults to local`() {
        val config = loadStartupConfig("   ")

        assertEquals(AppProfile.LOCAL, config.profile)
        assertEquals("memory", config.taskStorage)
    }

    @Test
    fun `given the local profile when loading then storage is memory`() {
        val config = loadStartupConfig("local")

        assertEquals(AppProfile.LOCAL, config.profile)
        assertEquals("memory", config.taskStorage)
    }

    @Test
    fun `given the live profile when loading then storage is obsidian`() {
        val config = loadStartupConfig("LIVE")

        assertEquals(AppProfile.LIVE, config.profile)
        assertEquals("obsidian", config.taskStorage)
    }

    @Test
    fun `given an unknown profile when loading then it fails naming the variable`() {
        val failure = assertFailsWith<IllegalArgumentException> { loadStartupConfig("staging") }

        assertTrue(failure.message!!.contains("APP_PROFILE"))
        assertTrue(failure.message!!.contains("local"))
        assertTrue(failure.message!!.contains("live"))
    }
}
