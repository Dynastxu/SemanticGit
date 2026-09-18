package com.github.semanticgit.ui.page

import com.github.semanticgit.core.AnalysisEngine

/**
 * 分析执行结果
 */
data class AnalysisRunResult(
    val success: Boolean,
    val failMessage: String = ""
)

/**
 * 分析执行器接口
 *
 * 目的是把"用什么配置去分析"从 UI 层解耦出来。
 * 目前 core 模块还没接收配置，所以默认实现忽略 settings，
 * 只调用原来的 AnalysisEngine()。
 *
 * 未来 core 支持解析配置后，只需新增一个实现类，
 * 把 settings 转成 ParserConfig / JavaParserConfig 传进 AnalysisEngine。
 */
interface AnalysisRunner {
    fun isDatabaseExists(repoPath: String, dbDir: String, settings: ParserSettings): Boolean
    fun fullAnalysis(repoPath: String, dbDir: String, settings: ParserSettings): AnalysisRunResult
}

/**
 * 默认实现：忽略 settings，走原来的无参 AnalysisEngine()。
 */
object DefaultAnalysisRunner : AnalysisRunner {

    override fun isDatabaseExists(
        repoPath: String,
        dbDir: String,
        settings: ParserSettings
    ): Boolean {
        // TODO: 等 core 支持配置后，把 settings 转换后传进去
        return AnalysisEngine().isDatabaseExists(repoPath, dbDir)
    }

    override fun fullAnalysis(
        repoPath: String,
        dbDir: String,
        settings: ParserSettings
    ): AnalysisRunResult {
        // TODO: 等 core 支持配置后，把 settings 转换后传进去
        return try {
            val engine = AnalysisEngine()
            val dbName = java.io.File(repoPath).absolutePath.hashCode().toString(16)

            // 使用新的异步 API，但在此处阻塞等待结果以保持接口兼容
            var errorMessage: String? = null
            val future = engine.fullAnalysisAsync(
                repoPath,
                dbDir,
                dbName,
                { /* 进度回调，暂不处理 */ },
                { e ->
                    errorMessage = e.message ?: "Unknown error"
                    null
                }
            )

            // 阻塞等待完成
            future.join()

            if (errorMessage != null) {
                AnalysisRunResult(success = false, failMessage = errorMessage)
            } else {
                AnalysisRunResult(success = true)
            }
        } catch (e: Exception) {
            AnalysisRunResult(
                success = false,
                failMessage = e.message ?: "Analysis failed"
            )
        }
    }
}