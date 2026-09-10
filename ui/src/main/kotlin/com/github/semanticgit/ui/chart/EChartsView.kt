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

@Composable
fun EChartsView(
    modifier: Modifier = Modifier,
    optionJson: String? = null,
    onChartClick: ((series: String, name: String, value: String) -> Unit)? = null
) {
    val jfxPanel = remember { JFXPanel() }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val pageReady = remember { mutableStateOf(false) }
    val callbackState = rememberUpdatedState(onChartClick)

    LaunchedEffect(Unit) {
        Platform.runLater {
            val webView = WebView()
            webView.engine.isJavaScriptEnabled = true

            // 1. 读取 ECharts JS 内容
            val echartsJs = javaClass.getResourceAsStream("/echarts.min.js")
                ?.bufferedReader()?.use { it.readText() }
                ?: run {
                    System.err.println("❌ 找不到 /echarts.min.js，请确认已放入 src/main/resources/")
                    ""
                }

            // 2. 读取 HTML 模板，把 JS 内联进去
            val htmlTemplate = javaClass.getResourceAsStream("/echarts_template.html")
                ?.bufferedReader()?.use { it.readText() }
                ?: "<html><body>未找到模板</body></html>"

            // 用 replace 而非 format，避免 JS 里的 $ 或 % 被误解析
            val html = htmlTemplate.replace("__ECHARTS_JS__", echartsJs)

            // 3. 监听页面加载完成
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
                            // 反射调用 setMember，兼容 JDK 25 模块限制
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

            // 4. 加载内联 HTML
            webView.engine.loadContent(html)

            webViewRef.value = webView
            jfxPanel.scene = Scene(webView)
        }
    }

    // 页面就绪后更新图表
    LaunchedEffect(optionJson, pageReady.value) {
        if (pageReady.value && optionJson != null) {
            webViewRef.value?.let { webView ->
                Platform.runLater { applyOption(webView, optionJson) }
            }
        }
    }

    SwingPanel(factory = { jfxPanel }, modifier = modifier)
}

private fun applyOption(webView: WebView, json: String) {
    // 用更稳妥的方式传递 JSON：通过 JSON.parse 而非字符串拼接
    val escaped = json
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ")
        .replace("\r", " ")
    webView.engine.executeScript("updateChart(JSON.parse('$escaped'))")
}
