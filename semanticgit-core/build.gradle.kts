dependencies {
    api(project(":semanticgit-parser-java"))
    api(project(":semanticgit-git"))
    implementation("org.xerial:sqlite-jdbc:${property("sqliteVersion")}")
}
