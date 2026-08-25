plugins {
    id("aidekit.multiplatform-conventions")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api(libs.kotlinx.coroutines.core)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
