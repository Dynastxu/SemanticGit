pluginManagement {
    plugins {
        kotlin("plugin.lombok") version "2.1.20"
    }
}
rootProject.name = "semanticgit"

include(
    "common",
    "parser:api",
    "parser:java",
    "parser:cpp",
    "parser:python",
    "git",
    "core",
    "ui",
    "cli"
)
