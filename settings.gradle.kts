pluginManagement {
    plugins {
        kotlin("plugin.lombok") version "2.1.20"
    }
}
rootProject.name = "semanticgit"

include(
    "semanticgit-common",
    "semanticgit-parser-api",
    "semanticgit-parser-java",
    "semanticgit-parser-cpp",
    "semanticgit-parser-python",
    "semanticgit-git",
    "semanticgit-core",
    "semanticgit-ui",
    "semanticgit-cli"
)
