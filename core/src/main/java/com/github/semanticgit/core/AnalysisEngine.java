package com.github.semanticgit.core;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Slf4j
public class AnalysisEngine extends AbstractAnalysisEngine {
    @Getter
    private String failReason = "";
    @Getter
    private String failMessage = "";

    @Override
    @Deprecated(forRemoval = true)
    public boolean fullAnalysis(String repoPath, String databaseDir) {
        return false;
    }

    @Override
    public CompletableFuture<Void> fullAnalysisAsync(String repoPath, String databaseDir, String databaseName, Callable<Float> onProgress, Function<Throwable, Void> onError) {
        int hash = Objects.hash(repoPath, databaseDir, databaseName);
        if (fullAnalysisFutures.containsKey(hash)) {
            if (fullAnalysisFutures.get(hash).isDone()) {
                fullAnalysisFutures.remove(hash);
            } else {
                return fullAnalysisFutures.get(hash);
            }
        }
        // TODO 实现
        return null;
    }

    @Override
    public CompletableFuture<Void> incrementalAnalysisAsync(String repoPath, File databaseFile, Callable<Float> onProgress, Function<Throwable, Void> onError) {
        int hash = Objects.hash(repoPath, databaseFile);
        if (incrementalAnalysisFutures.containsKey(hash)) {
            if (incrementalAnalysisFutures.get(hash).isDone()) {
                incrementalAnalysisFutures.remove(hash);
            } else {
                return incrementalAnalysisFutures.get(hash);
            }
        }
        // TODO 实现
        return null;
    }

    @Override
    @Deprecated(forRemoval = true)
    public boolean isDatabaseExists(String repoPath, String databaseDir) {
        String dbName = Integer.toHexString(new File(repoPath).getAbsolutePath().hashCode());
        File dbFile = new File(databaseDir, dbName + ".db");
        return dbFile.exists();
    }
}
