@file:Suppress("removal", "DEPRECATION")

package com.github.semanticgit.ui.view.chart

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.alpha
import com.github.semanticgit.common.entity.CommitMeta
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.scene.web.WebView
import kotlinx.coroutines.delay
import netscape.javascript.JSObject
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

private val logger = KotlinLogging.logger {}

private object GitGraphJavaFXBootstrap {
    @Volatile
    private var initialized = false

    @Synchronized
    fun ensure() {
        if (initialized) return
        try {
            Platform.setImplicitExit(false)
            initialized = true
            logger.debug("GitGraph JavaFX setImplicitExit(false) set")
        } catch (e: Exception) {
            logger.error(e) { "GitGraph JavaFX setImplicitExit(false) failed" }
        }
    }
}

data class RefInfo(
    val name: String,
    val oid: String,
    val kind: String
)

@Composable
fun GitGraphView(
    modifier: Modifier = Modifier,
    commits: List<CommitMeta> = emptyList(),
    refs: List<RefInfo> = emptyList(),
    onCommitClick: ((hash: String) -> Unit)? = null
) {
    val jfxPanel = remember {
        JFXPanel().also {
            it.isOpaque = false
            GitGraphJavaFXBootstrap.ensure()
        }
    }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val pageReady = remember { mutableStateOf(false) }
    val callbackState = rememberUpdatedState(onCommitClick)

    val targetAlpha = if (pageReady.value) 1f else 0f
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 500)
    )

    LaunchedEffect(Unit) {
        GitGraphJavaFXBootstrap.ensure()
        Platform.runLater {
            try {
                val webView = WebView()
                webView.engine.isJavaScriptEnabled = true

                val gitgraphJs = javaClass.getResourceAsStream("/gitgraph-bundle.js")
                    ?.bufferedReader()?.use { it.readText() }
                    ?: run {
                        logger.error("Cannot find /gitgraph-bundle.js")
                        ""
                    }

                val htmlTemplate = javaClass.getResourceAsStream("/gitgraph_template.html")
                    ?.bufferedReader()?.use { it.readText() }
                    ?: "<html><body>Template not found</body></html>"

                val html = htmlTemplate.replace("__GITGRAPH_JS__", gitgraphJs)

                webView.engine.loadWorker.stateProperty().addListener { _, _, state ->
                    if (state == Worker.State.SUCCEEDED) {
                        try {
                            val window = webView.engine.executeScript("window") as? JSObject
                            if (window != null) {
                                val bridge = object {
                                    @Suppress("unused")
                                    fun onCommitClick(hash: String) {
                                        logger.debug("GitGraph onCommitClick: hash=$hash")
                                        callbackState.value?.invoke(hash)
                                    }
                                }
                                val setMember = JSObject::class.java.getMethod(
                                    "setMember", String::class.java, Any::class.java
                                )
                                setMember.invoke(window, "javaBridge", bridge)
                                pageReady.value = true
                                logger.debug("GitGraph javaBridge mixin success")
                            }
                        } catch (e: Exception) {
                            logger.error(e) { "GitGraph javaBridge mixin failed" }
                        }
                    }
                }

                webView.engine.loadContent(html)
                webViewRef.value = webView
                val scene = Scene(webView)
                scene.fill = Color.TRANSPARENT
                jfxPanel.scene = scene

                jfxPanel.addMouseWheelListener { e ->
                    val wv = webViewRef.value ?: return@addMouseWheelListener
                    Platform.runLater {
                        wv.engine.executeScript("scrollBy(${e.wheelRotation * 60})")
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "GitGraphView init failed" }
                pageReady.value = false
            }
        }
    }

    val commitsJson = remember(commits) { commitsToJson(commits, refs) }

    LaunchedEffect(commitsJson, pageReady.value) {
        logger.debug { "LaunchedEffect data: pageReady=${pageReady.value}, commitsJson.length=${commitsJson.length}, webViewRef=${webViewRef.value != null}" }
        if (pageReady.value && commitsJson != null) {
            val webView = webViewRef.value
            if (webView == null) {
                logger.error { "webViewRef is null when pageReady=true!" }
                return@LaunchedEffect
            }
            Platform.runLater {
                try {
                    logger.debug { "calling applyData, jsonLen=${commitsJson.length}" }
                    applyData(webView, commitsJson)
                    logger.debug { "applyData done" }
                } catch (e: Exception) {
                    logger.error(e) { "applyData failed" }
                }
            }
        }
    }

    LaunchedEffect(pageReady.value) {
        if (!pageReady.value) return@LaunchedEffect
        var lastStamp = 0L
        while (true) {
            delay(100)
            Platform.runLater {
                try {
                    val wv = webViewRef.value ?: return@runLater
                    val stamp = (wv.engine.executeScript("window.__clickStamp") as? Number)?.toLong() ?: 0L
                    if (stamp > lastStamp) {
                        lastStamp = stamp
                        val oid = wv.engine.executeScript("window.__pendingOid") as? String
                        if (oid != null && oid.isNotEmpty()) {
                            logger.debug("GitGraph poll click: oid=$oid")
                            callbackState.value?.invoke(oid)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            logger.debug("GitGraphView dispose")
            val webView = webViewRef.value
            webViewRef.value = null
            pageReady.value = false

            Platform.runLater {
                try {
                    webView?.engine?.load(null)
                    webView?.engine?.loadWorker?.cancel()
                    jfxPanel.scene = null
                } catch (e: Exception) {
                    logger.error(e) { "GitGraphView dispose failed" }
                }
            }

            SwingUtilities.invokeLater {
                try {
                    jfxPanel.parent?.remove(jfxPanel)
                    logger.debug("GitGraph JFXPanel removed from AWT")
                } catch (e: Exception) {
                    logger.error(e) { "GitGraph JFXPanel remove failed" }
                }
            }
        }
    }

    SwingPanel(
        factory = { jfxPanel },
        modifier = modifier.alpha(animatedAlpha)
    )
}

private fun applyData(webView: WebView, json: String) {
    val escaped = json
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ")
        .replace("\r", " ")
    webView.engine.executeScript("updateData('$escaped')")
}

private fun commitsToJson(commits: List<CommitMeta>, refs: List<RefInfo>): String {
    val sb = StringBuilder()
    sb.append("{\"commits\":[")

    commits.forEachIndexed { index, commit ->
        if (index > 0) sb.append(",")
        sb.append("{")
        sb.append("\"oid\":\"${escapeJson(commit.hash ?: "")}\"")
        sb.append(",\"parents\":[")
        val parents = mutableListOf<String>()
        commit.parentCommitMeta?.hash?.let { parents.add(it) }
        commit.mergeParentMeta?.hash?.let { parents.add(it) }
        parents.forEachIndexed { pi, p ->
            if (pi > 0) sb.append(",")
            sb.append("\"${escapeJson(p)}\"")
        }
        sb.append("]")
        sb.append(",\"message\":\"${escapeJson(commit.message ?: "")}\"")
        sb.append(",\"author\":{")
        sb.append("\"name\":\"${escapeJson(commit.author?.name ?: "Unknown")}\"")
        val email = commit.author?.email
        if (!email.isNullOrBlank()) {
            sb.append(",\"email\":\"${escapeJson(email)}\"")
        }
        sb.append("}")
        val timestamp = commit.timestamp
        if (timestamp != null) {
            sb.append(",\"committedAt\":$timestamp")
        }
        sb.append("}")
    }

    sb.append("],\"refs\":[")

    refs.forEachIndexed { index, ref ->
        if (index > 0) sb.append(",")
        sb.append("{")
        sb.append("\"name\":\"${escapeJson(ref.name)}\"")
        sb.append(",\"oid\":\"${escapeJson(ref.oid)}\"")
        sb.append(",\"kind\":\"${escapeJson(ref.kind)}\"")
        sb.append("}")
    }

    sb.append("]}")
    return sb.toString()
}

private fun escapeJson(s: String): String {
    return s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
