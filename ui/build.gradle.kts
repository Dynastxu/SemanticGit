plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(25)
}

compose.desktop {
    application {
        mainClass = "com.github.semanticgit.ui.AppKt"
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(project(":core"))
    implementation("cafe.adriel.lyricist:lyricist:1.8.0")
}
