package com.github.semanticgit.ui.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.collections.plusAssign
import kotlin.collections.set


data class NodePos(val row: Int, val lane: Int)

data class EdgePath(
    val from: NodePos,
    val to: NodePos,
    val via: List<NodePos>
)

data class Dag<T>(val nodes: List<DagNode<T>>) {
    fun bfs(): List<DagNode<T>> {
        val visited = Collections.newSetFromMap(
            IdentityHashMap<DagNode<T>, Boolean>()
        )
        val queue = ArrayDeque<DagNode<T>>()
        val result = mutableListOf<DagNode<T>>()
        for (root in nodes) if (visited.add(root)) queue.add(root)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            result += cur
            for (child in cur.children) if (visited.add(child)) queue.add(child)
        }
        return result
    }
}

data class DagNode<T>(
    val children: List<DagNode<T>> = emptyList(),
    val data: T
)

data class GraphLayout(
    val nodes: List<NodePos>,
    val edges: List<EdgePath>
)

@Composable
fun <T> DagListView(
    modifier: Modifier = Modifier,
    dag: Dag<T>,
    comparator: Comparator<in T>,
    laneWidth: Dp = 10.dp,
    rowHeight: Dp = 20.dp,
    lineWidth: Dp = 2.dp,
    nodeRadius: Dp = 4.dp,
    rowBackground: @Composable (Boolean) -> Color = { isSelected -> if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent },
    lineColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    nodeColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.(T) -> Unit,
) {
    var selected by remember { mutableStateOf<Int?>(null) }

    val ordered = dag.bfs().sortedWith { a, b -> comparator.compare(a.data, b.data) }
    val layout = computeLayout(ordered)

    // 每一行的进入段（上半行）和离开段（下半行）
    data class Segment(val fromLane: Float, val toLane: Float)

    val incoming = mutableMapOf<Int, MutableList<Segment>>()
    val outgoing = mutableMapOf<Int, MutableList<Segment>>()

    for ((from, to, via) in layout.edges) {
        val path = buildList {
            add(from)
            addAll(via)
            add(to)
        }
        for (i in 0 until path.size - 1) {
            val a = path[i]
            val b = path[i + 1]
            if (a.lane == b.lane) {
                outgoing.getOrPut(a.row) { mutableListOf() } +=
                    Segment(a.lane.toFloat(), a.lane.toFloat())
                incoming.getOrPut(b.row) { mutableListOf() } +=
                    Segment(b.lane.toFloat(), b.lane.toFloat())
            } else {
                val mid = (a.lane + b.lane) / 2f
                outgoing.getOrPut(a.row) { mutableListOf() } +=
                    Segment(a.lane.toFloat(), mid)
                incoming.getOrPut(b.row) { mutableListOf() } +=
                    Segment(mid, b.lane.toFloat())
            }
        }
    }

    val nodeLaneByRow = layout.nodes.associate { it.row to it.lane }
    val maxLane = (
            layout.nodes.map { it.lane } +
                    layout.edges.flatMap { e -> e.via.map { it.lane } } +
                    layout.edges.flatMap { listOf(it.from.lane, it.to.lane) }
            ).maxOrNull() ?: 0
    val graphWidth = laneWidth * (maxLane + 1)

    LazyColumn(modifier = modifier.fillMaxSize()) {
        itemsIndexed(ordered) { index, node ->
            Row(
                modifier = Modifier
                    .height(rowHeight)
                    .fillMaxWidth()
                    .clickable { selected = index }
                    .background(rowBackground(index == selected)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(
                    modifier = Modifier
                        .width(graphWidth)
                        .fillMaxHeight()
                ) {
                    val lw = laneWidth.toPx()
                    val h = size.height
                    val midY = h / 2f
                    val stroke = lineWidth.toPx()
                    fun x(lane: Float) = lane * lw + lw / 2f

                    // 1. 进入：从上一行底部到中线
                    incoming[index]?.forEach { seg ->
                        drawLine(
                            color = lineColor,
                            start = Offset(x(seg.fromLane), 0f),
                            end = Offset(x(seg.toLane), midY),
                            strokeWidth = stroke,
                        )
                    }

                    // 2. 离开：从中线到下一行顶部（同 lane 竖线，跨 lane 斜线）
                    outgoing[index]?.forEach { seg ->
                        drawLine(
                            color = lineColor,
                            start = Offset(x(seg.fromLane), midY),
                            end = Offset(x(seg.toLane), h),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                        )
                    }

                    // 3. 节点：实心圆点（最上层）
                    nodeLaneByRow[index]?.let { lane ->
                        drawCircle(
                            color = nodeColor,
                            radius = nodeRadius.toPx(),
                            center = Offset(x(lane.toFloat()), midY),
                        )
                    }
                }

                content(node.data)
            }
        }
    }
}

private fun <T> computeLayout(ordered: List<DagNode<T>>): GraphLayout {
    val rowOf = IdentityHashMap<DagNode<T>, Int>()
    ordered.forEachIndexed { i, n -> rowOf[n] = i }
    val rowCount = ordered.size

    // ----- Step 1: 全局 lane 分配（经典 git lane 算法）-----
    val globalLane = IdentityHashMap<DagNode<T>, Int>()
    val lanes = mutableListOf<DagNode<T>?>()   // lanes[i] = 下一个期待出现的节点

    for ((row, node) in ordered.withIndex()) {
        val waiting = lanes.indices.filter { lanes[it] === node }
        val lane = if (waiting.isNotEmpty()) {
            waiting.first()                                  // 合并：汇聚到最左
        } else {
            val free = lanes.indexOfFirst { it == null }
            if (free >= 0) free else lanes.size.also { lanes.add(null) }
        }
        globalLane[node] = lane
        for (w in waiting) lanes[w] = null

        // 分配 children
        val children = node.children
            .filter { rowOf[it] != null && rowOf[it]!! != row }
            .sortedBy { rowOf[it] }
        if (children.isNotEmpty()) {
            lanes[lane] = children[0]                        // 主干继承当前 lane
            for (i in 1 until children.size) {
                val child = children[i]
                if (lanes.none { it === child }) {           // 已有人等它 → 合并
                    val free = lanes.indexOfFirst { it == null }
                    if (free >= 0) lanes[free] = child else lanes.add(child)
                }
            }
        }
    }

    // ----- Step 2: 收集边（每条边的全局 lane = 目的地节点的全局 lane）-----
    data class E(val fromRow: Int, val toRow: Int, val gLane: Int)
    val edges = mutableListOf<E>()
    for ((row, node) in ordered.withIndex()) {
        val children = node.children
            .filter { rowOf[it] != null && rowOf[it]!! != row }
            .sortedBy { rowOf[it] }
        for (child in children) {
            val sLane = globalLane[node]!!
            val tLane = globalLane[child]!!
            edges += E(row, rowOf[child]!!, maxOf(sLane, tLane))
        }
    }

    // ----- Step 3: 每一行紧凑化 -----
    // 该行用到的全局 lane 集合 = {当前节点的全局 lane} ∪ {所有穿过的边的全局 lane}
    // 排序后映射到 0,1,2,... 保证左侧不空
    val nodeLane = IntArray(rowCount)
    val edgeLane = Array(edges.size) { mutableMapOf<Int, Int>() }

    for (r in 0 until rowCount) {
        val node = ordered[r]
        val used = sortedSetOf<Int>()
        used += globalLane[node]!!
        for ((fromRow, toRow, gLane) in edges) if (r in (fromRow + 1)..toRow) used += gLane

        val mapping = HashMap<Int, Int>(used.size)
        used.forEachIndexed { i, gl -> mapping[gl] = i }

        nodeLane[r] = mapping[globalLane[node]!!]!!
        for ((ei, e) in edges.withIndex()) {
            if (r > e.fromRow && r <= e.toRow) {
                edgeLane[ei][r] = mapping[e.gLane]!!
            }
        }
    }

    // ----- Step 4: 组装 -----
    val nodePositions = List(ordered.size) { r -> NodePos(r, nodeLane[r]) }

    val edgePaths = edges.mapIndexed { ei, e ->
        EdgePath(
            from = nodePositions[e.fromRow],
            to = nodePositions[e.toRow],
            via = (e.fromRow + 1 until e.toRow).map { r ->
                NodePos(r, edgeLane[ei][r] ?: 0)
            }
        )
    }

    return GraphLayout(nodePositions, edgePaths)
}
