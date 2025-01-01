pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
//            if (requested.id.namespace == "org.jetbrains.kotlin") {
//                useVersion("2.0.21")
//            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "safe"
include(":app")
include(":app:preferences")
include(":app:SafeLinter")
include(":autolock")
include(":categorypager")
include(":clipboardutils")
include(":crypto")
include(":dateutils")
include(":gpm")
include(":hibp")
include(":notifications")
include(":oisaferestore")
include(":statemachine")
include(":safephoto")
