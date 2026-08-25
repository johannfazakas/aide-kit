import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

group = "ro.jf.ai"
version = "0.1.0"

val libs = the<VersionCatalogsExtension>().named("libs")

configure<KtlintExtension> {
    version.set(libs.findVersion("ktlint").get().requiredVersion)
}
