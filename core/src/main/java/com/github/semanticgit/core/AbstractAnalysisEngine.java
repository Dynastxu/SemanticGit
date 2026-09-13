package com.github.semanticgit.core;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

abstract class AbstractAnalysisEngine {
    protected Map<Integer, CompletableFuture<Void>> fullAnalysisFutures = new ConcurrentHashMap<>();
    protected Map<Integer, CompletableFuture<Void>> incrementalAnalysisFutures = new ConcurrentHashMap<>();

    /**
     * 全量分析
     *
     * @param repoPath    仓库路径
     * @param databaseDir 数据库保存的文件夹路径
     * @return 是否成功
     * @since d9.13
     * @deprecated Use {@link #fullAnalysisAsync} instead
     */
    @Deprecated(forRemoval = true)
    public abstract boolean fullAnalysis(String repoPath, String databaseDir);

    /**
     * 异步全量分析
     *
     * @param repoPath     仓库路径
     * @param databaseDir  数据库保存的文件夹路径
     * @param databaseName 数据库名称
     * @param onProgress   进度回调函数
     * @return 异步任务完成的 CompletableFuture 对象
     */
    public abstract CompletableFuture<Void> fullAnalysisAsync(String repoPath, String databaseDir, String databaseName, Callable<Float> onProgress, Function<Throwable, Void> onError);

    /**
     * 增量分析
     *
     * @param repoPath     仓库路径
     * @param databaseFile 数据库文件
     * @param onProgress   进度回调函数
     * @param onError      异常回调函数
     * @return 异步任务完成的 CompletableFuture 对象
     */
    public abstract CompletableFuture<Void> incrementalAnalysisAsync(String repoPath, File databaseFile, Callable<Float> onProgress, Function<Throwable, Void> onError);

    /**
     * 检查仓库对应的数据库文件是否已存在
     *
     * @param repoPath    仓库路径
     * @param databaseDir 数据库保存的文件夹路径
     * @return 数据库文件是否存在
     * @deprecated 由调用方自行判断
     */
    @Deprecated(forRemoval = true)
    public abstract boolean isDatabaseExists(String repoPath, String databaseDir);
}
