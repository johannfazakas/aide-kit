package ro.jf.ai.assistant.config

import com.typesafe.config.ConfigFactory

enum class AppProfile(
    val id: String,
) {
    LOCAL("local"),
    LIVE("live"),
    ;

    companion object {
        fun from(value: String?): AppProfile {
            val normalized = value?.trim()?.lowercase()?.ifBlank { null } ?: LOCAL.id
            return entries.firstOrNull { it.id == normalized }
                ?: throw IllegalArgumentException(
                    "APP_PROFILE must be one of ${entries.joinToString(", ") { it.id }}, got '$normalized'",
                )
        }
    }
}

data class StartupConfig(
    val profile: AppProfile = AppProfile.LOCAL,
    val openCodeApiKey: String? = null,
    val openCodeBaseUrl: String? = null,
    val port: String? = null,
    val corsAllowedOrigins: String? = null,
    val taskStorage: String? = null,
    val obsidianRepoUrl: String? = null,
    val obsidianRepoToken: String? = null,
    val obsidianRepoBranch: String? = null,
    val obsidianCloneDir: String? = null,
)

fun loadStartupConfig(profileValue: String? = System.getenv("APP_PROFILE")): StartupConfig {
    val profile = AppProfile.from(profileValue)
    val config = ConfigFactory.load("application-${profile.id}")

    fun value(path: String): String? =
        if (config.hasPath(path)) config.getString(path).trim().ifBlank { null } else null
    return StartupConfig(
        profile = profile,
        openCodeApiKey = value("opencode.apiKey"),
        openCodeBaseUrl = value("opencode.baseUrl"),
        port = value("server.port"),
        corsAllowedOrigins = value("server.corsAllowedOrigins"),
        taskStorage = value("storage"),
        obsidianRepoUrl = value("obsidian.repoUrl"),
        obsidianRepoToken = value("obsidian.token"),
        obsidianRepoBranch = value("obsidian.branch"),
        obsidianCloneDir = value("obsidian.cloneDir"),
    )
}
