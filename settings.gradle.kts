pluginManagement {
    repositories {
        // CI and Android builds: prefer canonical artifacts to avoid stale/broken mirror POMs.
        google()
        mavenCentral()
        gradlePluginPortal()
        // Fallback mirrors for networks where official endpoints are inaccessible.
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
    }
}
rootProject.name = "AssetStudioMobile"
include(":app")
