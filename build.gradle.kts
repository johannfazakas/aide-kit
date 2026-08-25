plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    id("aidekit.common-conventions")
}

tasks.named("check") {
    dependsOn(gradle.includedBuild("plugins").task(":check"))
}

tasks.register("buildImages") {
    dependsOn(":service:jibDockerBuild", ":client:dockerBuildImage")
}

tasks.register("installLocal") {
    dependsOn(tasks.named("build"))
    dependsOn(subprojects.map { "${it.path}:build" })
    dependsOn("buildImages")
}
