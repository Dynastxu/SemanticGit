dependencies {
    api(project(":parser:java"))
api(project(":git"))
    implementation("org.xerial:sqlite-jdbc:${property("sqliteVersion")}")
}
