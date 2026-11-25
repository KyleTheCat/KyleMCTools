pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "KyleMCTools"
include(":app", ":core", ":ui")
include(":tools:geyser-converter", ":tools:plugin-config", ":tools:pack-optimizer", ":tools:pack-protector", ":tools:meg-converter", ":tools:version-converter")
