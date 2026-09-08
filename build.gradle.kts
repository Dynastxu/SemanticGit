plugins {
    `java-library`
    id("io.freefair.lombok") version "8.13" apply false
}

allprojects {
    group = "com.github.semanticgit"
    version = property("revision") as String

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "io.freefair.lombok")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        implementation("org.slf4j:slf4j-simple:${property("slf4jVersion")}")
        implementation("org.jspecify:jspecify:${property("jspecifyVersion")}")
        compileOnly("org.jetbrains:annotations:${property("jetbrainsAnnotationsVersion")}")

        testImplementation("org.junit.jupiter:junit-jupiter:${property("junitVersion")}")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
