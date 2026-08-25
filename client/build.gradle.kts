plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    id("aidekit.common-conventions")
}

tasks.register<Exec>("dockerBuildImage") {
    dependsOn(tasks.named("wasmJsBrowserDistribution"))
    mustRunAfter(tasks.named("check"))
    inputs.dir(layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    inputs.file("Dockerfile")
    outputs.file(layout.buildDirectory.file("docker/imageId.txt"))
    workingDir = projectDir
    doFirst {
        layout.buildDirectory
            .dir("docker")
            .get()
            .asFile
            .mkdirs()
    }
    commandLine(
        "docker",
        "build",
        "--iidfile",
        "build/docker/imageId.txt",
        "-t",
        providers.gradleProperty("webImageName").get(),
        ".",
    )
}

kotlin {
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":client-core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}
