package com.github.semanticgit.ui.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.collections.ArrayDeque

// ---------------- 数据模型 ----------------

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

// ---------------- 布局：固定全局 lane + 每行紧凑 ----------------

fun <T> computeLayout(ordered: List<DagNode<T>>): GraphLayout {
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
            edges += E(row, rowOf[child]!!, globalLane[child]!!)
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
        for (e in edges) if (r > e.fromRow && r <= e.toRow) used += e.gLane

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
    val nodePositions = ordered.mapIndexed { r, _ -> NodePos(r, nodeLane[r]) }

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

// ---------------- Compose 组件 ----------------

@Composable
fun <T> DagListView(
    modifier: Modifier = Modifier,
    dag: Dag<T>,
    comparator: Comparator<in T>,
    laneWidth: Dp = 18.dp,
    rowHeight: Dp = 40.dp,
    lineColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    nodeColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.(T) -> Unit,
) {
    val ordered = dag.bfs().sortedWith { a, b -> comparator.compare(a.data, b.data) }
    val layout = computeLayout(ordered)

    // 每一行的进入段（上半行）和离开段（下半行）
    data class Segment(val fromLane: Float, val toLane: Float)

    val incoming = mutableMapOf<Int, MutableList<Segment>>()
    val outgoing = mutableMapOf<Int, MutableList<Segment>>()

    for (edge in layout.edges) {
        val path = buildList {
            add(edge.from)
            addAll(edge.via)
            add(edge.to)
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
                    .fillMaxWidth(),
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
                    val stroke = 2f
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
                            color = lineColor,
                            radius = lw * 0.3f,
                            center = Offset(x(lane.toFloat()), midY),
                        )
                    }
                }

                content(node.data)
            }
        }
    }
}

// ---------------- Demo ----------------

data class Commit(
    val hash: String,
    val message: String,
    val timestamp: Long,
)

fun buildTestDag(): Dag<Commit> {
    // 从新到旧的拓扑结构：
    //
    //     F            (Merge all features)      t=7
    //    / \
    //   E   B          (Merge parser fixes, Add lexer)  t=6, t=3
    //  / \   \
    // D   C   \        (Fix parser bug, Fix lexer bug)  t=5, t=4
    //  \ /    /
    //   A    /         (Add parser)              t=2
    //    \  /
    //     X            (Initial commit)          t=1
    //
    // 车道数：1 → 2 → 3 → 3 → 2 → 2 → 1

    val x = DagNode<Commit>(data = Commit("x000000", "Initial commit", 1))
    val a = DagNode(listOf(x), Commit("a000000", "Add parser", 2))
    val b = DagNode(listOf(x), Commit("b000000", "Add lexer", 3))
    val c = DagNode(listOf(a), Commit("c000000", "Fix lexer bug", 4))
    val d = DagNode(listOf(a), Commit("d000000", "Fix parser bug", 5))
    val e = DagNode(listOf(d, c), Commit("e000000", "Merge parser fixes", 6))
    val f = DagNode(listOf(e, b), Commit("f000000", "Merge all features", 7))

    return Dag(nodes = listOf(f))
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Commit History Graph Demo",
        state = rememberWindowState(width = 800.dp, height = 640.dp),
    ) {
        MaterialTheme { CommitHistoryDemo() }
    }
}

@Composable
fun CommitHistoryDemo() {
    val dag = buildTestDag()
    DagListView(
        dag = dag,
        comparator = compareByDescending<Commit> { it.timestamp }
            .thenBy { it.hash },
        modifier = Modifier.fillMaxSize().padding(16.dp),
        laneWidth = 18.dp,
        rowHeight = 44.dp,
    ) { commit ->
        Text(
            text = "${commit.hash}  ${commit.message}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
