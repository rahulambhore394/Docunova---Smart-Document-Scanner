pluginManagement {
    repositories {
        google()
        mavenCentral() // <-- Must be here
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral() // <-- This is the most important line for your error
    }
}
rootProject.name = "Docunova"
include(":app")

