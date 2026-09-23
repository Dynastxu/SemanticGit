plugins {
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.picocli)
}

application {
    mainClass.set("com.github.semanticgit.cli.Main")
}
