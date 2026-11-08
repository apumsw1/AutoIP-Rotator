pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AutoIPRotator"

include(":app")
include(":tor-android:tor-android-binary")
include(":lib-bitmask-core")
include(":lib-bitmask-core-web")
include(":lib-bitmask-core-arm64")
include(":lib-bitmask-core-armv7")
include(":lib-bitmask-core-x86")
include(":lib-bitmask-core-x86_64")