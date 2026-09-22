plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.lombok)
}
dependencies {
    api(project(":parser:java"))
    api(project(":git"))
    implementation(libs.jdbi3.core)
    implementation(libs.sqlite.jdbc)
    testImplementation(libs.kotlin.test)
}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(25)
}
