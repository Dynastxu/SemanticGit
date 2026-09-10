package com.github.semanticgit.core;

public abstract class AbstractAnalysisEngine {
    /**
     * 全量分析
     * @param repoPath 仓库路径
     * @param databaseDir 数据库保存的文件夹路径
     * @return 是否成功
     */
    public abstract boolean fullAnalysis(String repoPath, String databaseDir);

    /**
     * 检查仓库对应的数据库文件是否已存在
     * @param repoPath 仓库路径
     * @param databaseDir 数据库保存的文件夹路径
     * @return 数据库文件是否存在
     */
    public abstract boolean isDatabaseExists(String repoPath, String databaseDir);
}
