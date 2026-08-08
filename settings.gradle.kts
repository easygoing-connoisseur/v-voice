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
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // VOICEVOX CORE の Android AAR は Maven Central に無いため、
        // 公式リリースの java_packages.zip を展開したものをリポジトリとして持つ。
        maven { url = uri("${rootDir}/localrepo") }
    }
}

rootProject.name = "VoiceTester"
include(":app")
