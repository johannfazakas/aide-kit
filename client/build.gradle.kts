plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    id("com.android.application")
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
    jvmToolchain(21)
    androidTarget()
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
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.ktor.client.okhttp)
        }
    }
}

android {
    namespace = "ro.jf.ai.assistant.client"
    compileSdk =
        libs.versions.android.compile.sdk
            .get()
            .toInt()
    defaultConfig {
        applicationId = "ro.jf.ai.assistant"
        minSdk =
            libs.versions.android.min.sdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.compile.sdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) {
        it.enable = false
    }
}
