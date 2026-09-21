package com.github.semanticgit.core;

import com.github.semanticgit.core.dao.CommitMetaDao;
import com.github.semanticgit.core.dao.impl.CommitMetaDaoImpl;
import com.github.semanticgit.core.db.DatabaseManager;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnalysisEngine 增量分析")
class AnalysisEngineIncrementalAnalysisTest {

    @TempDir
    Path tempDir;

    private Path repoDir;
    private Path dbDir;
    private Git git;
    private AnalysisEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        repoDir = tempDir.resolve("repo");
        dbDir = tempDir.resolve("db");
        Files.createDirectories(dbDir);
        git = Git.init().setDirectory(repoDir.toFile()).call();
        engine = new AnalysisEngine();
    }

    @AfterEach
    void tearDown() {
        git.close();
    }

    private String commitFile(String path, String content, String message) throws Exception {
        Path file = repoDir.resolve(path);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
        git.add().addFilepattern(path).call();
        return git.commit()
                .setMessage(message)
                .setAuthor("Test User", "test@example.com")
                .call()
                .getName();
    }

    private File runFullAnalysis() throws Exception {
        String dbName = "incremental_test";
        CompletableFuture<Void> future = engine.fullAnalysisAsync(
                repoDir.toString(), dbDir.toString(), dbName,
                null, null);
        future.get();
        return new File(dbDir.toFile(), dbName + ".db");
    }

    @Nested
    @DisplayName("前置条件校验")
    class Preconditions {

        @Test
        @DisplayName("数据库文件不存在 → onError 收到 IllegalStateException")
        void databaseFileNotExists() {
            File nonExistent = new File(dbDir.toFile(), "nonexistent.db");

            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            CompletableFuture<Void> future = engine.incrementalAnalysisAsync(
                    repoDir.toString(), nonExistent, null, e -> {
                        errorRef.set(e);
                        return null;
                    });
            assertDoesNotThrow(() -> future.get());

            assertNotNull(errorRef.get());
            assertTrue(errorRef.get() instanceof CompletionException);
            Throwable cause = errorRef.get().getCause();
            assertInstanceOf(IllegalStateException.class, cause);
            assertTrue(cause.getMessage().contains("does not exist"));
        }

        @Test
        @DisplayName("数据库为空（未做过全量分析）→ onError 收到 IllegalStateException")
        void emptyDatabase() throws Exception {
            commitFile("test.txt", "test", "init");
            File dbFile = new File(dbDir.toFile(), "empty.db");
            try (DatabaseManager dm = new DatabaseManager(dbFile, true)) {
                // 创建空库（只有表结构，无数据）
            }

            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            CompletableFuture<Void> future = engine.incrementalAnalysisAsync(
                    repoDir.toString(), dbFile, null, e -> {
                        errorRef.set(e);
                        return null;
                    });
            assertDoesNotThrow(() -> future.get());

            assertNotNull(errorRef.get());
            assertTrue(errorRef.get() instanceof CompletionException);
            Throwable cause = errorRef.get().getCause();
            assertInstanceOf(IllegalStateException.class, cause);
            assertTrue(cause.getMessage().contains("empty"));
        }
    }

    @Nested
    @DisplayName("正常增量分析")
    class NormalFlow {

        @Test
        @DisplayName("没有新提交 → progress 回调 1.0")
        void noNewCommits() throws Exception {
            commitFile("a.txt", "content A", "first commit");
            File dbFile = runFullAnalysis();

            AtomicReference<Float> progress = new AtomicReference<>(-1.0f);
            CompletableFuture<Void> future = engine.incrementalAnalysisAsync(
                    repoDir.toString(), dbFile, progress::set, null);
            future.get();

            assertEquals(1.0f, progress.get(), 0.01f);
        }

        @Test
        @DisplayName("有新提交 → 增量分析成功，新commit写入数据库")
        void newCommitsAnalyzed() throws Exception {
            String hash1 = commitFile("a.txt", "content A", "first");
            commitFile("b.txt", "content B", "second");
            String hash3 = commitFile("c.txt", "content C", "third");

            File dbFile = runFullAnalysis();

            // 全量分析后，继续追加新提交
            String hash4 = commitFile("d.txt", "content D", "fourth");
            String hash5 = commitFile("e.txt", "content E", "fifth");

            // 执行增量分析
            CompletableFuture<Void> future = engine.incrementalAnalysisAsync(
                    repoDir.toString(), dbFile, null, null);
            future.get();

            // 验证数据库中新commit已写入
            try (DatabaseManager dm = new DatabaseManager(dbFile, false)) {
                CommitMetaDao dao = new CommitMetaDaoImpl(dm);
                assertEquals(5, dao.count());
                assertNotNull(dao.findByHash(hash4));
                assertNotNull(dao.findByHash(hash5));
                assertEquals("fourth", dao.findByHash(hash4).getMessage());
                assertEquals("fifth", dao.findByHash(hash5).getMessage());
            }
        }

        @Test
        @DisplayName("HEAD 未变化 → already up to date")
        void headUnchanged() throws Exception {
            commitFile("a.txt", "content A", "first");
            File dbFile = runFullAnalysis();

            // 不做任何新提交，再次执行增量分析
            AtomicReference<Float> progress = new AtomicReference<>(-1.0f);
            CompletableFuture<Void> future = engine.incrementalAnalysisAsync(
                    repoDir.toString(), dbFile, progress::set, null);
            future.get();

            assertEquals(1.0f, progress.get(), 0.01f);
        }
    }

    @Nested
    @DisplayName("异常场景")
    class ErrorScenarios {

        @Test
        @DisplayName("数据库为空时 onError 回调被触发")
        void emptyDbTriggersOnError() throws Exception {
            commitFile("test.txt", "test", "init");
            File dbFile = new File(dbDir.toFile(), "empty_for_error.db");
            try (DatabaseManager dm = new DatabaseManager(dbFile, true)) {
                // 空库
            }

            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            Function<Throwable, Void> onError = e -> {
                errorRef.set(e);
                return null;
            };

            CompletableFuture<Void> future = engine.incrementalAnalysisAsync(
                    repoDir.toString(), dbFile, null, onError);
            future.get();

            assertNotNull(errorRef.get());
            assertTrue(errorRef.get() instanceof CompletionException);
            assertInstanceOf(IllegalStateException.class, errorRef.get().getCause());
        }

        @Test
        @DisplayName("数据库文件不存在时 onError 回调被触发")
        void missingDbTriggersOnError() throws Exception {
            commitFile("test.txt", "test", "init");
            File dbFile = new File(dbDir.toFile(), "will_not_exist.db");

            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            Function<Throwable, Void> onError = e -> {
                errorRef.set(e);
                return null;
            };

            CompletableFuture<Void> future = engine.incrementalAnalysisAsync(
                    repoDir.toString(), dbFile, null, onError);
            future.get();

            assertNotNull(errorRef.get());
            assertTrue(errorRef.get() instanceof CompletionException);
            assertInstanceOf(IllegalStateException.class, errorRef.get().getCause());
        }
    }

    @Nested
    @DisplayName("进度回调")
    class ProgressCallback {

        @Test
        @DisplayName("多个新提交 → progress 从低到高依次回调，最终到 1.0")
        void progressIncreasesWithCommits() throws Exception {
            commitFile("a.txt", "content", "first");
            File dbFile = runFullAnalysis();

            commitFile("b.txt", "content", "second");
            commitFile("c.txt", "content", "third");
            commitFile("d.txt", "content", "fourth");

            AtomicReference<Float> lastProgress = new AtomicReference<>(0.0f);
            AtomicReference<Float> finalProgress = new AtomicReference<>(-1.0f);
            Consumer<Float> onProgress = p -> {
                assertTrue(p >= lastProgress.get(), "progress should be non-decreasing");
                lastProgress.set(p);
                finalProgress.set(p);
            };

            CompletableFuture<Void> future = engine.incrementalAnalysisAsync(
                    repoDir.toString(), dbFile, onProgress, null);
            future.get();

            assertEquals(1.0f, finalProgress.get(), 0.01f);
        }
    }

    @Nested
    @DisplayName("并发去重")
    class Deduplication {

        @Test
        @DisplayName("相同的 (repoPath, databaseFile) → 返回同一个 Future")
        void sameKeyReturnsSameFuture() throws Exception {
            commitFile("a.txt", "content", "first");
            File dbFile = runFullAnalysis();

            CompletableFuture<Void> future1 = engine.incrementalAnalysisAsync(
                    repoDir.toString(), dbFile, null, null);
            CompletableFuture<Void> future2 = engine.incrementalAnalysisAsync(
                    repoDir.toString(), dbFile, null, null);

            assertSame(future1, future2);
            future1.get();
        }
    }
}
