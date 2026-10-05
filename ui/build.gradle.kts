plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.javafxplugin)
}

kotlin {
    jvmToolchain(25)
}

javafx {
    version = "25"
    modules("javafx.web", "javafx.swing")
}

tasks.withType<Test> {
    failOnNoDiscoveredTests = false   // 允许没有测试
}

val javafxSdkLib = "C:/Program Files/Java/javafx-sdk-25.0.4/lib"
val commonJvmArgs = listOf(
    "--enable-native-access=javafx.graphics,javafx.web,ALL-UNNAMED",
    "--upgrade-module-path=$javafxSdkLib",
    "--module-path=$javafxSdkLib",
    "--add-modules=javafx.web,javafx.swing,jdk.jsobject"
)

compose.desktop {
    application {
        mainClass = "com.github.semanticgit.ui.AppKt"
        jvmArgs += commonJvmArgs
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(project(":core"))
    implementation(libs.lyricist)
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)
}

tasks.register<JavaExec>("runEChartsDemo") {
    group = "demo"
    description = "Run ECharts Demo"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.github.semanticgit.ui.view.chart.EChartsViewDemoKt")
    jvmArgs = commonJvmArgs
}

tasks.register<JavaExec>("runDagListDemo") {
    group = "demo"
    description = "Run DAG List Demo"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.github.semanticgit.ui.view.chart.DagListViewDemoKt")
    jvmArgs = commonJvmArgs
}

tasks.register<JavaExec>("runPieChartDemo") {
    group = "demo"
    description = "Run Pie Chart Demo"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.github.semanticgit.ui.view.chart.PieChartDemoKt")
    jvmArgs = commonJvmArgs
}

tasks.register<JavaExec>("runBarChartDemo") {
    group = "demo"
    description = "Run Bar Chart Demo"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.github.semanticgit.ui.view.chart.BarChartDemoKt")
    jvmArgs = commonJvmArgs
}

tasks.register<JavaExec>("runMultiChartDisplayDemo") {
    group = "demo"
    description = "Run Multi Chart Display Demo"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.github.semanticgit.ui.view.chart.MultiChartDisplayDemoKt")
    jvmArgs = commonJvmArgs
}
