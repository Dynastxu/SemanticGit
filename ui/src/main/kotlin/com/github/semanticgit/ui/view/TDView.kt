package com.github.semanticgit.ui.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CommitNode(
    val hash: String,
    val authorName: String,
    val timestamp: Int,
    val message: String,
    val isMerge: Boolean,
    val parentHash: String?,
    val mergeParentHash: String?,
    val depth: Int,
    val lane: Int
)

@Composable
fun CommitTopologyGraph(
    nodes: List<CommitNode>,
    selectedHash: String?,
    onCommitClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (nodes.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface

    val rowHeight = 28.dp
    val laneWidth = 16.dp
    val nodeRadius = 4.dp
    val textSize = 11.sp
    val colHash = 78.dp
    val colAuthor = 64.dp
    val colTime = 112.dp

    val sorted = nodes.sortedByDescending { it.depth }
    val hashToNode = nodes.associateBy { it.hash }

    val mainSet = mutableSetOf<String>()
    var chain: String? = sorted.firstOrNull()?.hash
    while (chain != null) {
        mainSet.add(chain)
        chain = hashToNode[chain]?.parentHash
    }

    val laneByHash = mutableMapOf<String, Int>()
    mainSet.forEach { laneByHash[it] = 0 }

    val mergeNodes = sorted.filter { it.isMerge && it.mergeParentHash != null }
    var nextLane = 1
    for (mergeNode in mergeNodes.sortedBy { it.depth }) {
        val mergeParentHash = mergeNode.mergeParentHash ?: continue
        if (mergeParentHash in laneByHash) continue

        var cur: String = mergeParentHash
        while (true) {
            if (cur in laneByHash) break
            laneByHash[cur] = nextLane
            val parent = hashToNode[cur]?.parentHash ?: break
            cur = parent
        }
        nextLane++
    }

    sorted.forEach { node ->
        if (node.hash !in laneByHash) laneByHash[node.hash] = 0
    }

    val maxLane = (laneByHash.values.maxOrNull() ?: 0)
    val laneColors = listOf(
        Color(0xFF4A90D9),
        Color(0xFF50B86C),
        Color(0xFFE85D75),
        Color(0xFF9C27B0),
        Color(0xFF00BCD4),
        Color(0xFFFF7043),
        Color(0xFF607D8B),
        Color(0xFFF9A825)
    )

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val rowHeightPx: Float
    val laneWidthPx: Float
    val nodeRadiusPx: Float
    val colHashPx: Float
    val colAuthorPx: Float
    val colTimePx: Float
    val densityScale: Float

    with(LocalDensity.current) {
        rowHeightPx = rowHeight.toPx()
        laneWidthPx = laneWidth.toPx()
        nodeRadiusPx = nodeRadius.toPx()
        colHashPx = colHash.toPx()
        colAuthorPx = colAuthor.toPx()
        colTimePx = colTime.toPx()
        densityScale = 1f.dp.toPx()
    }

    val labelLeft = (maxLane + 1) * laneWidthPx + 24f
    val totalWidth = labelLeft + colHashPx + colAuthorPx + colTimePx + 400f
    val totalHeight = sorted.size * rowHeightPx + 16f

    val hashToIndex = sorted.withIndex().associate { (idx, node) -> node.hash to idx }

    Box(modifier = modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Canvas(
                    modifier = Modifier
                        .width((totalWidth / densityScale).dp)
                        .height((totalHeight / densityScale).dp)
                ) {
                    val dimStyle = TextStyle(fontSize = textSize, color = onSurface.copy(alpha = 0.6f))
                    val strokeWidth = 2f

                    for (lane in 0..maxLane) {
                        val commitsOnLane = sorted.withIndex()
                            .filter { laneByHash[it.value.hash] == lane }
                            .sortedBy { it.index }
                        for (i in 0 until commitsOnLane.size - 1) {
                            val (topIdx, _) = commitsOnLane[i]
                            val (bottomIdx, _) = commitsOnLane[i + 1]
                            val topY = topIdx * rowHeightPx + rowHeightPx / 2
                            val bottomY = bottomIdx * rowHeightPx + rowHeightPx / 2
                            val x = laneWidthPx * lane + laneWidthPx / 2
                            drawLine(
                                color = laneColors[lane % laneColors.size].copy(alpha = 0.5f),
                                start = Offset(x, topY + nodeRadiusPx),
                                end = Offset(x, bottomY - nodeRadiusPx),
                                strokeWidth = strokeWidth
                            )
                        }
                    }

                    for (nodeIdx in sorted.indices) {
                        val node = sorted[nodeIdx]
                        val y = nodeIdx * rowHeightPx + rowHeightPx / 2
                        val lane = laneByHash[node.hash] ?: 0
                        val nodeX = laneWidthPx * lane + laneWidthPx / 2

                        val parentHash = node.parentHash
                        if (parentHash != null) {
                            val parentLane = laneByHash[parentHash]
                            val parentIdx = hashToIndex[parentHash]
                            if (parentLane != null && parentLane != lane && parentIdx != null) {
                                val parentX = laneWidthPx * parentLane + laneWidthPx / 2
                                val parentY = parentIdx * rowHeightPx + rowHeightPx / 2
                                val connColor = laneColors[lane % laneColors.size].copy(alpha = 0.7f)
                                drawLine(
                                    color = connColor,
                                    start = Offset(parentX, parentY),
                                    end = Offset(nodeX, parentY),
                                    strokeWidth = strokeWidth
                                )
                                drawLine(
                                    color = connColor,
                                    start = Offset(nodeX, parentY),
                                    end = Offset(nodeX, y),
                                    strokeWidth = strokeWidth
                                )
                            }
                        }

                        if (node.isMerge && node.mergeParentHash != null) {
                            val mergeParentLane = laneByHash[node.mergeParentHash]
                            val mergeParentIdx = hashToIndex[node.mergeParentHash]
                            if (mergeParentLane != null && mergeParentLane != lane && mergeParentIdx != null && mergeParentIdx < sorted.size) {
                                val mergeX = laneWidthPx * mergeParentLane + laneWidthPx / 2
                                val mergeY = mergeParentIdx * rowHeightPx + rowHeightPx / 2
                                val gold = Color(0xFFF9A825).copy(alpha = 0.7f)
                                drawLine(
                                    color = gold,
                                    start = Offset(nodeX, y),
                                    end = Offset(mergeX, y),
                                    strokeWidth = strokeWidth
                                )
                                drawLine(
                                    color = gold,
                                    start = Offset(mergeX, y),
                                    end = Offset(mergeX, mergeY),
                                    strokeWidth = strokeWidth
                                )
                            }
                        }

                        val laneColor = laneColors[lane % laneColors.size]
                        val dotColor = when {
                            node.hash == selectedHash -> Color(0xFFFFD54F)
                            node.isMerge -> Color(0xFFF9A825)
                            else -> laneColor
                        }
                        val dotRadius = if (node.isMerge) nodeRadiusPx + 1f else nodeRadiusPx
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(nodeX, y))
                        if (node.hash == selectedHash) {
                            drawCircle(color = dotColor.copy(alpha = 0.3f), radius = dotRadius + 3f, center = Offset(nodeX, y))
                        }

                        val labelX = labelLeft
                        val textY = y - textSize.toPx() / 2
                        val hashText = node.hash.take(8)
                        val textColor = if (node.isMerge) Color(0xFFF9A825) else onSurface
                        drawText(
                            textLayoutResult = textMeasurer.measure(text = hashText, style = TextStyle(fontSize = textSize, color = textColor)),
                            topLeft = Offset(labelX, textY)
                        )

                        val xAuthor = labelX + colHashPx
                        val authorLabel = if (node.authorName.length > 6) node.authorName.take(5) + "\u2026" else node.authorName
                        drawText(textLayoutResult = textMeasurer.measure(text = authorLabel, style = dimStyle), topLeft = Offset(xAuthor, textY))

                        val timeStr = dateFormatter.format(Date(node.timestamp * 1000L))
                        val xTime = labelX + colHashPx + colAuthorPx
                        drawText(textLayoutResult = textMeasurer.measure(text = timeStr, style = dimStyle), topLeft = Offset(xTime, textY))

                        val xMsg = labelX + colHashPx + colAuthorPx + colTimePx
                        val maxMsgWidth = ((totalWidth - xMsg) / densityScale).toInt().coerceAtLeast(100)
                        val msgLayout = textMeasurer.measure(
                            text = node.message,
                            style = TextStyle(fontSize = textSize, color = textColor),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            constraints = Constraints(maxWidth = maxMsgWidth)
                        )
                        drawText(textLayoutResult = msgLayout, topLeft = Offset(xMsg, textY))
                    }
                }
            }
        }
    }
}
