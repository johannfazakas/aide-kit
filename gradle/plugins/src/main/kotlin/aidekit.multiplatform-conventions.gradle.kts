import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("aidekit.common-conventions")
}

configure<KotlinMultiplatformExtension> {
    jvmToolchain(21)
    jvm()
    androidTarget()
    wasmJs {
        browser {
            testTask {
                enabled = false
            }
        }
    }
}

val versionCatalog = the<VersionCatalogsExtension>().named("libs")

configure<LibraryExtension> {
    namespace = "ro.jf.ai.assistant.${project.name.replace("-", "")}"
    compileSdk =
        versionCatalog
            .findVersion("android-compile-sdk")
            .get()
            .requiredVersion
            .toInt()
    defaultConfig {
        minSdk =
            versionCatalog
                .findVersion("android-min-sdk")
                .get()
                .requiredVersion
                .toInt()
    }
}

configure<LibraryAndroidComponentsExtension> {
    beforeVariants(selector().withBuildType("release")) {
        it.enable = false
    }
}
