dependencies {
    api(project(":parser:api"))
    implementation("com.github.javaparser:javaparser-core:${property("javaparserVersion")}")
}
