pluginManagement {
    includeBuild("gradle/plugins")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "aide-kit"

includeBuild("gradle/plugins")

include("service")
include("shared")
