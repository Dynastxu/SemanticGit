package com.github.semanticgit.ui.view

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

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

    val x = DagNode(data = Commit("x000000", "Initial commit", 1))
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
        title = "DAG List View Demo",
        state = rememberWindowState(width = 800.dp, height = 640.dp),
    ) {
        MaterialTheme { DagListViewDemo() }
    }
}

@Composable
fun DagListViewDemo() {
    val dag = buildTestDag()
    DagListView(
        dag = dag,
        comparator = compareByDescending<Commit> { it.timestamp }
            .thenBy { it.hash },
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) { commit ->
        Text(
            text = "${commit.hash}  ${commit.message}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
