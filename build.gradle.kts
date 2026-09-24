plugins {
    `java-library`
    alias(libs.plugins.lombok) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val versionCatalogLibs = libs

allprojects {
    group = "com.github.semanticgit"
    version = property("version") as String

    repositories {
        mavenCentral()
        google()
    }
}

subprojects {
    if (name == "ui") return@subprojects

    apply(plugin = "java-library")
    apply(plugin = "io.freefair.lombok")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    dependencies {
        implementation(versionCatalogLibs.slf4j.simple)
        implementation(versionCatalogLibs.jspecify)
        compileOnly(versionCatalogLibs.jetbrains.annotations)

        testImplementation(versionCatalogLibs.junit.jupiter)
        testRuntimeOnly(versionCatalogLibs.junit.platform.launcher)
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
