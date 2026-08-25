import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("aidekit.common-conventions")
}

configure<KotlinMultiplatformExtension> {
    jvmToolchain(21)
    jvm()
    wasmJs {
        browser {
            testTask {
                enabled = false
            }
        }
    }
}
