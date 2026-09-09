package com.github.semanticgit.core;

import com.github.semanticgit.core.dto.SimpleEntityChangeStatistics;

import java.io.File;

public abstract class AbstractAnalysisEngine {
    /**
     * 全量分析
     * @param repoPath 仓库路径
     * @param databasePath 数据库保存的文件夹路径
     * @return 是否成功
     */
    public abstract boolean fullAnalysis(String repoPath, String databasePath);
    public abstract SimpleEntityChangeStatistics getSimpleEntityChangeStatistics(File databaseFile);
}
