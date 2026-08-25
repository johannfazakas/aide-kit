plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

ktlint {
    version.set(libs.versions.ktlint.asProvider())
    filter {
        exclude { it.file.path.contains("generated") }
    }
}

dependencies {
    implementation(libs.ktlint.gradle.plugin)
}
