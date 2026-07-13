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
        // Локальные .aar (Glyph Matrix SDK) кладутся в app/libs
        flatDir { dirs("app/libs") }
    }
}

rootProject.name = "GlyphWeather"
include(":app")
