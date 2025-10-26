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

rootProject.name = "safe"

include(":app")
include(":app:backup")
include(":app:cryptoobjects")
include(":app:datamodel")
include(":app:db")
include(":app:preferences")
include(":app:theme")
include(":app:SafeLinter")

include(":autolock")
include(":categorypager")
include(":clipboardutils")
include(":crypto")
include(":datepicker")
include(":dateutils")
include(":gpm")
include(":gpmdatamodel")
include(":gpmui")
include(":hibp")
include(":logger")
include(":notifications")
include(":oisaferestore")
include(":safephoto")
include(":statemachine")
