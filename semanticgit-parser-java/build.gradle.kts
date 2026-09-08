dependencies {
    api(project(":semanticgit-parser-api"))
    implementation("com.github.javaparser:javaparser-core:${property("javaparserVersion")}")
}
