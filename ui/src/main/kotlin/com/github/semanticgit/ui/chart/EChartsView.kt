@file:Suppress("removal")

package com.github.semanticgit.ui.chart

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import javax.swing.SwingUtilities

/**
 * ★ 关键：JavaFX 平台全局初始化。
 *
 * JavaFX 默认在最后一个窗口/面板关闭时退出平台。
 * 我们在这里调用 setImplicitExit(false)，让平台永不退出。
 * 这样 EChartsView 被销毁-重建时，平台依然存活。
 */
private object JavaFXBootstrap {
    @Volatile
    private var initialized = false

    @Synchronized
    fun ensure() {
        if (initialized) return
        try {
            Platform.setImplicitExit(false)
            initialized = true
            println("✅ JavaFX setImplicitExit(false) 已设置")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun EChartsView(
    modifier: Modifier = Modifier,
    optionJson: String? = null,
    onChartClick: ((series: String, name: String, value: String) -> Unit)? = null
) {
    // ★ 创建 JFXPanel 后立即设置 setImplicitExit(false)
    val jfxPanel = remember {
        JFXPanel().also {
            JavaFXBootstrap.ensure()
        }
    }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val pageReady = remember { mutableStateOf(false) }
    val callbackState = rememberUpdatedState(onChartClick)

    LaunchedEffect(Unit) {
        JavaFXBootstrap.ensure()   // 双保险
        Platform.runLater {
            try {
                val webView = WebView()
                webView.engine.isJavaScriptEnabled = true

                val echartsJs = javaClass.getResourceAsStream("/echarts.min.js")
                    ?.bufferedReader()?.use { it.readText() }
                    ?: run {
                        System.err.println("❌ 找不到 /echarts.min.js")
                        ""
                    }

                val htmlTemplate = javaClass.getResourceAsStream("/echarts_template.html")
                    ?.bufferedReader()?.use { it.readText() }
                    ?: "<html><body>未找到模板</body></html>"

                val html = htmlTemplate.replace("__ECHARTS_JS__", echartsJs)

                webView.engine.loadWorker.stateProperty().addListener { _, _, state ->
                    if (state == Worker.State.SUCCEEDED) {
                        try {
                            val window = webView.engine.executeScript("window") as? JSObject
                            if (window != null) {
                                val bridge = object {
                                    @Suppress("unused")
                                    fun onChartClick(series: String, name: String, value: String) {
                                        callbackState.value?.invoke(series, name, value)
                                    }
                                }
                                val setMember = JSObject::class.java.getMethod(
                                    "setMember", String::class.java, Any::class.java
                                )
                                setMember.invoke(window, "javaBridge", bridge)
                                pageReady.value = true
                                println("✅ javaBridge 注入成功")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                webView.engine.loadContent(html)
                webViewRef.value = webView
                jfxPanel.scene = Scene(webView)
            } catch (e: Exception) {
                e.printStackTrace()
                pageReady.value = false
            }
        }
    }

    LaunchedEffect(optionJson, pageReady.value) {
        if (pageReady.value && optionJson != null) {
            webViewRef.value?.let { webView ->
                Platform.runLater {
                    try {
                        applyOption(webView, optionJson)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            println("🧹 EChartsView 开始清理")
            val webView = webViewRef.value
            webViewRef.value = null
            pageReady.value = false

            // JavaFX 线程：停止加载、断开 Scene
            Platform.runLater {
                try {
                    webView?.engine?.load(null)
                    webView?.engine?.loadWorker?.cancel()
                    jfxPanel.scene = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // ★ AWT 线程：从父容器移除 JFXPanel（否则会浮在屏幕上）
            SwingUtilities.invokeLater {
                try {
                    jfxPanel.parent?.remove(jfxPanel)
                    println("🧹 JFXPanel 已从 AWT 移除")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    SwingPanel(factory = { jfxPanel }, modifier = modifier)
}

private fun applyOption(webView: WebView, json: String) {
    val escaped = json
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ")
        .replace("\r", " ")
    webView.engine.executeScript("updateChart(JSON.parse('$escaped'))")
}