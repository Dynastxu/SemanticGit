package com.github.semanticgit.core;

import com.github.semanticgit.common.entity.Author;
import com.github.semanticgit.common.entity.CommitMeta;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    @DisplayName("仓库损坏时返回 false")
    void fullAnalysis_CorruptedRepo_ReturnsFalse() throws Exception {
        tempRepoDir = Files.createTempDirectory("semanticgit-test-corrupt-repo");
        tempDbDir = Files.createTempDirectory("semanticgit-test-db");

        Path gitDir = tempRepoDir.resolve(".git");
        Files.createDirectory(gitDir);
        Files.writeString(gitDir.resolve("HEAD"), "0123456789abcdef0123456789abcdef01234567\n");

        boolean result = engine.fullAnalysis(
                tempRepoDir.toString(),
                tempDbDir.toString()
        );
        assertFalse(result, "仓库损坏时 fullAnalysis 应返回 false");
    }

    @Test
    @DisplayName("空仓库（无提交）应返回 true，数据库无提交记录")
    void fullAnalysis_EmptyRepo_ReturnsTrue() throws Exception {
        tempRepoDir = Files.createTempDirectory("semanticgit-test-empty-repo");
        tempDbDir = Files.createTempDirectory("semanticgit-test-db");

        //noinspection EmptyTryBlock
        try (Git ignored = Git.init().setDirectory(tempRepoDir.toFile()).call()) {
            // 空仓库，不做任何提交
        }

        boolean result = engine.fullAnalysis(
                tempRepoDir.toString(),
                tempDbDir.toString()
        );
        assertTrue(result, "空仓库分析应返回 true");

        // 验证数据库已创建但没有提交记录
        String repoName = Integer.toHexString(
                new File(tempRepoDir.toAbsolutePath().toString()).getAbsolutePath().hashCode()
        );
        File dbFile = new File(tempDbDir.toFile(), repoName + ".db");
        assertTrue(dbFile.exists(), "数据库文件应被创建");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath().replace("\\", "/"));
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM commit_meta")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "空仓库不应有提交记录");
        }
    }

    @Test
    @DisplayName("有提交的仓库应返回 true，且数据库正确保存所有提交")
    void fullAnalysis_ValidRepoWithCommits_ReturnsTrue() throws Exception {
        tempRepoDir = Files.createTempDirectory("semanticgit-test-repo");
        tempDbDir = Files.createTempDirectory("semanticgit-test-db");

        List<CommitMeta> expectedCommits = createTestRepoWithCommits(tempRepoDir);

        boolean result = engine.fullAnalysis(
                tempRepoDir.toString(),
                tempDbDir.toString()
        );
        assertTrue(result, "有效仓库分析应返回 true");

        // 验证数据库中的提交记录
        String repoName = Integer.toHexString(
                new File(tempRepoDir.toAbsolutePath().toString()).getAbsolutePath().hashCode()
        );
        File dbFile = new File(tempDbDir.toFile(), repoName + ".db");
        assertTrue(dbFile.exists(), "数据库文件应被创建");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath().replace("\\", "/"));
             Statement stmt = conn.createStatement()) {

            // 验证提交数量
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM commit_meta")) {
                assertTrue(rs.next());
                assertEquals(expectedCommits.size(), rs.getInt(1), "提交数量应匹配");
            }

            // 验证每个提交的 hash 都已保存
            for (CommitMeta expected : expectedCommits) {
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT hash, timestamp, message FROM commit_meta WHERE hash = '" + expected.getHash() + "'")) {
                    assertTrue(rs.next(), "应找到提交: " + expected.getHash());
                    assertEquals(expected.getTimestamp(), rs.getInt("timestamp"), "时间戳应匹配");
                    assertEquals(expected.getMessage(), rs.getString("message"), "提交信息应匹配");
                }
            }

            // 验证作者信息
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM author")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) > 0, "应有作者记录");
            }
        }
    }

    @Test
    @DisplayName("连续两次 fullAnalysis 应都能成功（覆盖模式）")
    void fullAnalysis_TwiceInSameDatabase_ShouldOverwrite() throws Exception {
        tempRepoDir = Files.createTempDirectory("semanticgit-test-repo-twice");
        tempDbDir = Files.createTempDirectory("semanticgit-test-db");

        createTestRepoWithCommits(tempRepoDir);

        // 第一次分析
        boolean firstResult = engine.fullAnalysis(
                tempRepoDir.toString(),
                tempDbDir.toString()
        );
        assertTrue(firstResult, "第一次分析应成功");

        // 第二次分析（overwrite=true，应覆盖数据库）
        boolean secondResult = engine.fullAnalysis(
                tempRepoDir.toString(),
                tempDbDir.toString()
        );
        assertTrue(secondResult, "第二次分析应成功");

        // 验证数据库中的数据（不应重复）
        String repoName = Integer.toHexString(
                new File(tempRepoDir.toAbsolutePath().toString()).getAbsolutePath().hashCode()
        );
        File dbFile = new File(tempDbDir.toFile(), repoName + ".db");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath().replace("\\", "/"));
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM commit_meta")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1), "覆盖后提交数量应不重复");
        }
    }

    @Test
    @DisplayName("非源码文件（.txt）提交不应产生 ChangeLog 记录")
    void fullAnalysis_NonSourceFile_NoChangeLog() throws Exception {
        tempRepoDir = Files.createTempDirectory("semanticgit-test-non-source");
        tempDbDir = Files.createTempDirectory("semanticgit-test-db");

        createTestRepoWithCommits(tempRepoDir);

        boolean result = engine.fullAnalysis(
                tempRepoDir.toString(),
                tempDbDir.toString()
        );
        assertTrue(result, "分析应成功");

        String repoName = Integer.toHexString(
                new File(tempRepoDir.toAbsolutePath().toString()).getAbsolutePath().hashCode()
        );
        File dbFile = new File(tempDbDir.toFile(), repoName + ".db");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath().replace("\\", "/"));
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM change_log")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), ".txt 文件不应产生 ChangeLog 记录");
            }
        }
    }

    @Test
    @DisplayName("Java 源码新增提交应产生 ADD 类型的 ChangeLog 记录")
    void fullAnalysis_JavaFileAdded_CreatesAddChangeLogs() throws Exception {
        tempRepoDir = Files.createTempDirectory("semanticgit-test-java-add");
        tempDbDir = Files.createTempDirectory("semanticgit-test-db");

        git = Git.init().setDirectory(tempRepoDir.toFile()).call();

        Path javaFile = tempRepoDir.resolve("Hello.java");
        Files.writeString(javaFile, """
                public class Hello {
                    public void greet() {
                        System.out.println("Hello");
                    }
                }
                """);
        git.add().addFilepattern("Hello.java").call();
        PersonIdent author = new PersonIdent("Dev", "dev@example.com");
        git.commit()
                .setAuthor(author)
                .setCommitter(author)
                .setMessage("Add Hello.java")
                .call();

        boolean result = engine.fullAnalysis(
                tempRepoDir.toString(),
                tempDbDir.toString()
        );
        assertTrue(result, "分析应成功");

        String repoName = Integer.toHexString(
                new File(tempRepoDir.toAbsolutePath().toString()).getAbsolutePath().hashCode()
        );
        File dbFile = new File(tempDbDir.toFile(), repoName + ".db");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath().replace("\\", "/"));
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM change_log")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) > 0, "Java 文件应产生 ChangeLog 记录");
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT cl.file_path, cl.operation, e.name, e.kind " +
                            "FROM change_log cl JOIN entity e ON cl.entity_id = e.id " +
                            "WHERE cl.operation = " + com.github.semanticgit.common.entity.ChangeOperation.ADD.code)) {
                boolean hasAdd = false;
                while (rs.next()) {
                    hasAdd = true;
                    assertEquals("Hello.java", rs.getString("file_path"));
                    assertEquals(com.github.semanticgit.common.entity.ChangeOperation.ADD.code, rs.getInt("operation"));
                }
                assertTrue(hasAdd, "应有 ADD 类型的 ChangeLog");
            }
        }
    }

    @Test
    @DisplayName("Java 源码修改提交应正确识别 ADD/REMOVE/MODIFY 实体变更")
    void fullAnalysis_JavaFileModified_DetectsEntityChanges() throws Exception {
        tempRepoDir = Files.createTempDirectory("semanticgit-test-java-modify");
        tempDbDir = Files.createTempDirectory("semanticgit-test-db");

        git = Git.init().setDirectory(tempRepoDir.toFile()).call();

        Path javaFile = tempRepoDir.resolve("Service.java");
        Files.writeString(javaFile, """
                public class Service {
                    public void oldMethod() {
                    }
                
                    public void keepMethod() {
                    }
                }
                """);
        git.add().addFilepattern("Service.java").call();
        PersonIdent author = new PersonIdent("Dev", "dev@example.com");
        git.commit()
                .setAuthor(author)
                .setCommitter(author)
                .setMessage("Add Service.java with oldMethod and keepMethod")
                .call();

        Files.writeString(javaFile, """
                public class Service {
                    public void newMethod() {
                    }
                
                    public void keepMethod() {
                    }
                }
                """);
        git.add().addFilepattern("Service.java").call();
        git.commit()
                .setAuthor(author)
                .setCommitter(author)
                .setMessage("Replace oldMethod with newMethod, keep keepMethod")
                .call();

        boolean result = engine.fullAnalysis(
                tempRepoDir.toString(),
                tempDbDir.toString()
        );
        assertTrue(result, "分析应成功");

        String repoName = Integer.toHexString(
                new File(tempRepoDir.toAbsolutePath().toString()).getAbsolutePath().hashCode()
        );
        File dbFile = new File(tempDbDir.toFile(), repoName + ".db");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath().replace("\\", "/"));
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT e.name, cl.operation FROM change_log cl " +
                            "JOIN entity e ON cl.entity_id = e.id " +
                            "ORDER BY e.name")) {
                List<String> changes = new ArrayList<>();
                while (rs.next()) {
                    changes.add(rs.getString("name") + ":" + rs.getInt("operation"));
                }
                assertFalse(changes.isEmpty(), "应有 ChangeLog 记录");

                boolean hasRemoveOldMethod = changes.stream()
                        .anyMatch(c -> c.contains("oldMethod") && c.contains(String.valueOf(com.github.semanticgit.common.entity.ChangeOperation.REMOVE.code)));
                boolean hasAddNewMethod = changes.stream()
                        .anyMatch(c -> c.contains("newMethod") && c.contains(String.valueOf(com.github.semanticgit.common.entity.ChangeOperation.ADD.code)));
                boolean hasModifyKeepMethod = changes.stream()
                        .anyMatch(c -> c.contains("keepMethod") && c.contains(String.valueOf(com.github.semanticgit.common.entity.ChangeOperation.MODIFY.code)));

                assertTrue(hasRemoveOldMethod, "oldMethod 应被标记为 REMOVE");
                assertTrue(hasAddNewMethod, "newMethod 应被标记为 ADD");
                assertTrue(hasModifyKeepMethod, "keepMethod 应被标记为 MODIFY");
            }
        }
    }

    @Test
    @DisplayName("Java 源码删除提交应产生 REMOVE 类型的 ChangeLog")
    void fullAnalysis_JavaFileDeleted_CreatesRemoveChangeLogs() throws Exception {
        tempRepoDir = Files.createTempDirectory("semanticgit-test-java-delete");
        tempDbDir = Files.createTempDirectory("semanticgit-test-db");

        git = Git.init().setDirectory(tempRepoDir.toFile()).call();

        Path javaFile = tempRepoDir.resolve("ToDelete.java");
        Files.writeString(javaFile, """
                public class ToDelete {
                    public void method() {
                    }
                }
                """);
        git.add().addFilepattern("ToDelete.java").call();
        PersonIdent author = new PersonIdent("Dev", "dev@example.com");
        git.commit()
                .setAuthor(author)
                .setCommitter(author)
                .setMessage("Add ToDelete.java")
                .call();

        Files.delete(javaFile);
        git.rm().addFilepattern("ToDelete.java").call();
        git.commit()
                .setAuthor(author)
                .setCommitter(author)
                .setMessage("Delete ToDelete.java")
                .call();

        boolean result = engine.fullAnalysis(
                tempRepoDir.toString(),
                tempDbDir.toString()
        );
        assertTrue(result, "分析应成功");

        String repoName = Integer.toHexString(
                new File(tempRepoDir.toAbsolutePath().toString()).getAbsolutePath().hashCode()
        );
        File dbFile = new File(tempDbDir.toFile(), repoName + ".db");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath().replace("\\", "/"));
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT cl.operation, e.name FROM change_log cl " +
                            "JOIN entity e ON cl.entity_id = e.id " +
                            "WHERE cl.operation = " + com.github.semanticgit.common.entity.ChangeOperation.REMOVE.code)) {
                boolean hasRemove = false;
                while (rs.next()) {
                    hasRemove = true;
                    assertEquals(com.github.semanticgit.common.entity.ChangeOperation.REMOVE.code, rs.getInt("operation"));
                }
                assertTrue(hasRemove, "应有 REMOVE 类型的 ChangeLog");
            }
        }
    }

    @Test
    @DisplayName("混合源码和非源码提交，非源码文件被跳过不影响分析")
    void fullAnalysis_MixedSourceAndNonSource_SkipsNonSource() throws Exception {
        tempRepoDir = Files.createTempDirectory("semanticgit-test-mixed");
        tempDbDir = Files.createTempDirectory("semanticgit-test-db");

        git = Git.init().setDirectory(tempRepoDir.toFile()).call();

        Path javaFile = tempRepoDir.resolve("App.java");
        Files.writeString(javaFile, """
                public class App {
                    public static void main(String[] args) {
                    }
                }
                """);
        Files.writeString(tempRepoDir.resolve("README.md"), "# Test Project");
        git.add().addFilepattern(".").call();
        PersonIdent author = new PersonIdent("Dev", "dev@example.com");
        git.commit()
                .setAuthor(author)
                .setCommitter(author)
                .setMessage("Add App.java and README.md")
                .call();

        boolean result = engine.fullAnalysis(
                tempRepoDir.toString(),
                tempDbDir.toString()
        );
        assertTrue(result, "分析应成功");

        String repoName = Integer.toHexString(
                new File(tempRepoDir.toAbsolutePath().toString()).getAbsolutePath().hashCode()
        );
        File dbFile = new File(tempDbDir.toFile(), repoName + ".db");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath().replace("\\", "/"));
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM change_log")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) > 0, "Java 文件应产生 ChangeLog");

                try (ResultSet rs2 = stmt.executeQuery(
                        "SELECT DISTINCT file_path FROM change_log")) {
                    while (rs2.next()) {
                        String filePath = rs2.getString("file_path");
                        assertTrue(filePath.endsWith(".java"),
                                "ChangeLog 应只包含 .java 文件，但发现: " + filePath);
                    }
                }
            }
        }
    }

    private List<CommitMeta> createTestRepoWithCommits(Path repoDir) throws Exception {
        List<CommitMeta> commits = new ArrayList<>();

        git = Git.init().setDirectory(repoDir.toFile()).call();

        // 第一个提交
        Path file1 = repoDir.resolve("file1.txt");
        Files.writeString(file1, "Hello World");
        git.add().addFilepattern("file1.txt").call();
        PersonIdent author1 = new PersonIdent("Alice", "alice@example.com");
        git.commit()
                .setAuthor(author1)
                .setCommitter(author1)
                .setMessage("Initial commit")
                .call();

        CommitMeta meta1 = CommitMeta.builder()
                .hash(getHeadHash())
                .author(Author.builder().name("Alice").email("alice@example.com").build())
                .timestamp(getHeadCommitTime())
                .message("Initial commit")
                .build();
        commits.add(meta1);

        // 第二个提交
        Path file2 = repoDir.resolve("file2.txt");
        Files.writeString(file2, "Second file");
        git.add().addFilepattern("file2.txt").call();
        PersonIdent author2 = new PersonIdent("Bob", "bob@example.com");
        git.commit()
                .setAuthor(author2)
                .setCommitter(author2)
                .setMessage("Add second file")
                .call();

        CommitMeta meta2 = CommitMeta.builder()
                .hash(getHeadHash())
                .author(Author.builder().name("Bob").email("bob@example.com").build())
                .timestamp(getHeadCommitTime())
                .message("Add second file")
                .build();
        commits.add(meta2);

        return commits;
    }

    private @Nullable String getHeadHash() throws Exception {
        ObjectId headId = git.getRepository().resolve("HEAD");
        return headId != null ? headId.getName() : null;
    }

    private int getHeadCommitTime() throws Exception {
        try (RevWalk walk = new RevWalk(git.getRepository())) {
            ObjectId headId = git.getRepository().resolve("HEAD");
            if (headId != null) {
                return walk.parseCommit(headId).getCommitTime();
            }
        }
        return 0;
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
