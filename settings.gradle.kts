pluginManagement {
    includeBuild("gradle/plugins")
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "aide-kit"

includeBuild("gradle/plugins")

include("service")
include("shared")
include("client-core")
include("client")
