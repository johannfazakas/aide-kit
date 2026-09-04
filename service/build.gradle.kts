plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jib)
    application
    id("aidekit.common-conventions")
}

val jibArchitecture =
    providers.gradleProperty("dockerArchitecture").orNull
        ?: if (System.getProperty("os.arch") == "aarch64") "arm64" else "amd64"

jib {
    from {
        image = "eclipse-temurin:21-jre"
        platforms {
            platform {
                architecture = jibArchitecture
                os = "linux"
            }
        }
    }
    to {
        image = providers.gradleProperty("serviceImageName").get()
    }
    container {
        ports = listOf("7080")
    }
}

tasks.named("jibDockerBuild") {
    mustRunAfter(tasks.named("check"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("ro.jf.ai.assistant.ApplicationKt")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.koog.ktor)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(libs.logback.classic)
    implementation(libs.jgit)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
}

tasks.test {
    useJUnitPlatform()
}
