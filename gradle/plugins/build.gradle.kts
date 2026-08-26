plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(21)
}

ktlint {
    version.set(libs.versions.ktlint.asProvider())
    filter {
        exclude { it.file.path.contains("generated") }
    }
}

dependencies {
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.gradle.plugin)
}
