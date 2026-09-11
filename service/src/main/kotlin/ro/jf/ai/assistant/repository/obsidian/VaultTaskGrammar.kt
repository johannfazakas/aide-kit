package ro.jf.ai.assistant.repository.obsidian

object VaultTaskGrammar {
    const val FIELD_INDENT = "      "

    val checkboxLine = Regex("""^(\s*[-*+] \[)([ xX-])(]\s+)(.*)$""")

    val inlineField = Regex("""\[([A-Za-z_][\w-]*)::\s*([^]]*)]""")

    val tasksHeading = Regex("""(?im)^#{1,2}[ \t]+tasks\b.*$""")

    fun fieldRegex(key: String): Regex = Regex("""\[$key::\s*[^]]*]""")

    fun boldTitle(title: String): String = "**$title**"

    fun field(
        key: String,
        value: String,
    ): String = "[$key:: $value]"
}
