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
include(":app:preferences")
include(":app:SafeLinter")
include(":app:db")
include(":app:cryptoobjects")
include(":app:theme")
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
include(":datepicker")
include(":gpmui")
include(":logger")
include(":app:backup")
include(":app:datamodel")
include(":gpmdatamodel")
