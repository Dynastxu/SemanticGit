plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.lombok")
}
dependencies {
    api(project(":parser:java"))
    api(project(":git"))
    implementation("org.xerial:sqlite-jdbc:${property("sqliteVersion")}")
    testImplementation(kotlin("test"))
}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(25)
}