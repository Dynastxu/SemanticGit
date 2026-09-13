package com.github.semanticgit.core;

import org.eclipse.jgit.api.Git;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.nio.file.Path;

class AnalysisEngineTest {

    private AnalysisEngine engine;
    private Path tempRepoDir;
    private Path tempDbDir;
    private Git git;

    @BeforeEach
    void setUp() {
        engine = new AnalysisEngine();
    }

    @AfterEach
    void tearDown() {
        if (git != null) {
            git.close();
            git = null;
        }
        cleanupTempDirs();
    }

    private void cleanupTempDirs() {
        if (tempRepoDir != null) {
            deleteRecursively(tempRepoDir.toFile());
        }
        if (tempDbDir != null) {
            deleteRecursively(tempDbDir.toFile());
        }
    }

    private void deleteRecursively(@NonNull File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete() && file.exists()) {
            file.setWritable(true);
            file.delete();
        }
    }
}
