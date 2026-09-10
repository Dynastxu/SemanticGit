plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

kotlin {
    jvmToolchain(25)
}

javafx {
    version = "25"
    modules("javafx.web", "javafx.swing")
}

val javafxSdkLib = "C:/Program Files/Java/javafx-sdk-25.0.4/lib"

compose.desktop {
    application {
        mainClass = "com.github.semanticgit.ui.AppKt"
        jvmArgs += listOf(
            "--enable-native-access=javafx.graphics,javafx.web,ALL-UNNAMED",
            "--upgrade-module-path=$javafxSdkLib",
            "--module-path=$javafxSdkLib",
            "--add-modules=javafx.web,javafx.swing,jdk.jsobject"
        )
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(project(":core"))
    implementation("cafe.adriel.lyricist:lyricist:1.8.0")
}
