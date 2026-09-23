plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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
