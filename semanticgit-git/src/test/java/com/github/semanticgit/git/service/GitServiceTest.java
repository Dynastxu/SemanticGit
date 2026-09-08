package com.github.semanticgit.git.service;

import com.github.semanticgit.git.dto.GitCommitInfo;
import com.github.semanticgit.git.dto.GitDiffEntry;
import com.github.semanticgit.common.entity.ChangeOperation;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GitServiceTest {

    private Path tempDir;
    private Git setupGit;
    private GitService gitService;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("git-service-test");
        setupGit = Git.init().setDirectory(tempDir.toFile()).call();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (gitService != null) {
            gitService.close();
        }
        if (setupGit != null) {
            setupGit.close();
        }
        try (var files = Files.walk(tempDir)) {
            files.sorted(Comparator.reverseOrder())
                 .map(Path::toFile)
                 .forEach(f -> {
                     if (!f.delete()) f.deleteOnExit();
                 });
        }
    }

    private @NonNull String commitFile(String fileName, String content, String message) throws IOException, GitAPIException {
        Path file = tempDir.resolve(fileName);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        setupGit.add().addFilepattern(fileName).call();
        return setupGit.commit()
                .setMessage(message)
                .setAuthor("Test User", "test@example.com")
                .call()
                .getName();
    }

    private @NonNull String commit(String message) throws GitAPIException {
        return setupGit.commit()
                .setMessage(message)
                .setAuthor("Test User", "test@example.com")
                .call()
                .getName();
    }

    @NonNull
    private String latestCommitHash() throws GitAPIException {
        return setupGit.log().call().iterator().next().getName();
    }

    // ======================== 构造函数测试 ========================

    @Test
    @DisplayName("有效仓库路径应成功构造 GitService")
    void testConstructorWithValidRepoPath() throws Exception {
        commitFile("test.txt", "test", "init");
        gitService = new GitService(tempDir.toString());
        assertNotNull(gitService);
    }

    @Test
    @DisplayName("传入工作区路径（不含 .git 后缀）应能正确构造并正常使用")
    void testConstructorWithWorkspacePath() throws Exception {
        commitFile("test.txt", "test", "init");
        String hash = latestCommitHash();
        gitService = new GitService(tempDir.toString());
        assertNotNull(gitService);
        List<GitDiffEntry> diffs = gitService.getDiffBetweenCommits(hash, hash);
        assertTrue(diffs.isEmpty());
    }

    // ======================== getDiffBetweenCommits 测试 ========================

    @Test
    @DisplayName("应正确检测 ADD/MODIFY/DELETE 三种操作及其文件内容")
    void testGetDiffBetweenCommits() throws Exception {
        commitFile("a.txt", "content A", "first commit");
        String commit1 = commitFile("b.txt", "content B", "add b.txt");

        Files.writeString(tempDir.resolve("a.txt"), "modified A", StandardCharsets.UTF_8);
        setupGit.add().addFilepattern("a.txt").call();

        commitFile("c.txt", "content C", "add c.txt");

        setupGit.rm().addFilepattern("b.txt").call();

        String commit2 = commit("second commit with add/modify/delete");

        gitService = new GitService(tempDir.toString());
        List<GitDiffEntry> diffs = gitService.getDiffBetweenCommits(commit1, commit2);

        assertNotNull(diffs);
        assertEquals(3, diffs.size());

        for (GitDiffEntry entry : diffs) {
            switch (entry.getNewPath()) {
                case "a.txt" -> {
                    assertEquals(ChangeOperation.MODIFY, entry.getChangeOperation());
                    assertEquals("a.txt", entry.getOldPath());
                    assertEquals("content A", entry.getOldContent());
                    assertEquals("modified A", entry.getNewContent());
                }
                case "c.txt" -> {
                    assertEquals(ChangeOperation.ADD, entry.getChangeOperation());
                    assertNull(entry.getOldContent());
                    assertEquals("content C", entry.getNewContent());
                }
                case null -> {
                    assertEquals(ChangeOperation.REMOVE, entry.getChangeOperation());
                    assertEquals("b.txt", entry.getOldPath());
                    assertEquals("content B", entry.getOldContent());
                    assertNull(entry.getNewContent());
                }
                default -> fail("Unexpected file in diff: " + entry.getNewPath());
            }
        }
    }

    @Test
    @DisplayName("相同 commit 之间 diff 应返回空列表")
    void testGetDiffBetweenSameCommit() throws Exception {
        String commit = commitFile("test.txt", "test", "init");
        gitService = new GitService(tempDir.toString());
        List<GitDiffEntry> diffs = gitService.getDiffBetweenCommits(commit, commit);

        assertNotNull(diffs);
        assertTrue(diffs.isEmpty());
    }

    @Test
    @DisplayName("两个无效 hash 应抛出")
    void testGetDiffWithBothInvalidHashes() throws Exception {
        commitFile("test.txt", "test", "init");
        gitService = new GitService(tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> gitService.getDiffBetweenCommits("deadbeef", "cafebabe"));
    }

    @Test
    @DisplayName("一个有效一个无效 hash 应抛出")
    void testGetDiffWithOneInvalidHash() throws Exception {
        String commit = commitFile("test.txt", "test", "init");
        gitService = new GitService(tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> gitService.getDiffBetweenCommits(commit, "deadbeef"));

        assertThrows(IllegalArgumentException.class, () -> gitService.getDiffBetweenCommits("deadbeef", commit));
    }

    // ======================== ChangeType 映射测试 ========================

    @Test
    @DisplayName("MODIFY 操作应映射为 ChangeOperation.MODIFY")
    void testModifyOperation() throws Exception {
        String commit1 = commitFile("modify.txt", "original", "initial");
        Files.writeString(tempDir.resolve("modify.txt"), "changed", StandardCharsets.UTF_8);
        setupGit.add().addFilepattern("modify.txt").call();
        String commit2 = commit("modify file");

        gitService = new GitService(tempDir.toString());
        List<GitDiffEntry> diffs = gitService.getDiffBetweenCommits(commit1, commit2);

        assertEquals(1, diffs.size());
        assertEquals(ChangeOperation.MODIFY, diffs.getFirst().getChangeOperation());
        assertEquals("original", diffs.getFirst().getOldContent());
        assertEquals("changed", diffs.getFirst().getNewContent());
    }

    @Test
    @DisplayName("ADD 操作应映射为 ChangeOperation.ADD 且 oldContent 为 null")
    void testAddOperation() throws Exception {
        String commit1 = commitFile("existing.txt", "existing", "initial");
        commitFile("new.txt", "new file", "add new file");
        String commit2 = latestCommitHash();

        gitService = new GitService(tempDir.toString());
        List<GitDiffEntry> diffs = gitService.getDiffBetweenCommits(commit1, commit2);

        GitDiffEntry addedEntry = diffs.stream()
                .filter(e -> "new.txt".equals(e.getNewPath()))
                .findFirst()
                .orElseThrow();

        assertEquals(ChangeOperation.ADD, addedEntry.getChangeOperation());
        assertNull(addedEntry.getOldContent());
        assertEquals("new file", addedEntry.getNewContent());
    }

    @Test
    @DisplayName("DELETE 操作应映射为 ChangeOperation.REMOVE 且 newContent 为 null")
    void testDeleteOperation() throws Exception {
        String commit1 = commitFile("to_delete.txt", "delete me", "initial");
        setupGit.rm().addFilepattern("to_delete.txt").call();
        String commit2 = commit("delete file");

        gitService = new GitService(tempDir.toString());
        List<GitDiffEntry> diffs = gitService.getDiffBetweenCommits(commit1, commit2);

        assertEquals(1, diffs.size());
        GitDiffEntry deletedEntry = diffs.getFirst();
        assertEquals(ChangeOperation.REMOVE, deletedEntry.getChangeOperation());
        assertEquals("to_delete.txt", deletedEntry.getOldPath());
        assertEquals("delete me", deletedEntry.getOldContent());
        assertNull(deletedEntry.getNewContent());
    }

    // ======================== close 测试 ========================

    @Test
    @DisplayName("close 方法应正常关闭资源，不抛出异常")
    void testClose() throws Exception {
        commitFile("test.txt", "test", "init");
        gitService = new GitService(tempDir.toString());
        assertDoesNotThrow(() -> gitService.close());
    }

    @Test
    @DisplayName("重复调用 close 不应抛出异常")
    void testCloseMultipleTimes() throws Exception {
        commitFile("test.txt", "test", "init");
        gitService = new GitService(tempDir.toString());
        gitService.close();
        assertDoesNotThrow(() -> gitService.close());
    }

    // ======================== 边界情况测试 ========================

    @Test
    @DisplayName("空文件内容的 diff 应正确返回")
    void testEmptyFileContent() throws Exception {
        String commit1 = commitFile("empty.txt", "", "initial");
        Files.writeString(tempDir.resolve("empty.txt"), "now has content", StandardCharsets.UTF_8);
        setupGit.add().addFilepattern("empty.txt").call();
        String commit2 = commit("modify empty file");

        gitService = new GitService(tempDir.toString());
        List<GitDiffEntry> diffs = gitService.getDiffBetweenCommits(commit1, commit2);

        assertEquals(1, diffs.size());
        assertEquals(ChangeOperation.MODIFY, diffs.getFirst().getChangeOperation());
        assertEquals("", diffs.getFirst().getOldContent());
        assertEquals("now has content", diffs.getFirst().getNewContent());
    }

    @Test
    @DisplayName("无任何变更的两次提交之间 diff 应返回空列表")
    void testNoChangesBetweenCommits() throws Exception {
        String commit1 = commitFile("same.txt", "unchanged", "initial");
        String commit2 = commit("empty commit with no changes");

        gitService = new GitService(tempDir.toString());
        List<GitDiffEntry> diffs = gitService.getDiffBetweenCommits(commit1, commit2);

        assertNotNull(diffs);
        assertTrue(diffs.isEmpty());
    }

    // ======================== getCommitsBetween 测试 ========================

    @Test
    @DisplayName("两个提交之间应返回正确的提交信息（单个提交）")
    void testGetCommitsBetweenSingleCommit() throws Exception {
        String commit1 = commitFile("a.txt", "content A", "first commit");
        String commit2 = commitFile("b.txt", "content B", "second commit");

        gitService = new GitService(tempDir.toString());
        List<GitCommitInfo> commits = gitService.getCommitsBetween(commit1, commit2);

        assertNotNull(commits);
        assertEquals(1, commits.size());

        GitCommitInfo info = commits.getFirst();
        assertEquals(commit2, info.getCommitHash());
        assertEquals("Test User", info.getAuthorName());
        assertEquals("test@example.com", info.getAuthorEmail());
        assertEquals("second commit", info.getFullMessage());
        assertNotNull(info.getDiffEntries());
        assertEquals(1, info.getDiffEntries().size());

        GitDiffEntry diff = info.getDiffEntries().getFirst();
        assertEquals(ChangeOperation.ADD, diff.getChangeOperation());
        assertEquals("b.txt", diff.getNewPath());
        assertEquals("content B", diff.getNewContent());
    }

    @Test
    @DisplayName("多个提交之间应返回所有中间提交，且按时间倒序排列")
    void testGetCommitsBetweenMultipleCommits() throws Exception {
        String commit1 = commitFile("a.txt", "content A", "first commit");
        String commit2 = commitFile("b.txt", "content B", "second commit");
        String commit3 = commitFile("c.txt", "content C", "third commit");

        gitService = new GitService(tempDir.toString());
        List<GitCommitInfo> commits = gitService.getCommitsBetween(commit1, commit3);

        assertNotNull(commits);
        assertEquals(2, commits.size());

        assertEquals(commit3, commits.get(0).getCommitHash());
        assertEquals(commit2, commits.get(1).getCommitHash());
    }

    @Test
    @DisplayName("应正确返回 ADD/MODIFY/REMOVE 操作的 diff 信息")
    void testGetCommitsBetweenDiffOperations() throws Exception {
        String commit1 = commitFile("a.txt", "original A", "first commit");
        commitFile("b.txt", "content B", "add b.txt");

        Files.writeString(tempDir.resolve("a.txt"), "modified A", StandardCharsets.UTF_8);
        setupGit.add().addFilepattern("a.txt").call();

        setupGit.rm().addFilepattern("b.txt").call();

        String commit3 = commit("modify a.txt and delete b.txt");

        gitService = new GitService(tempDir.toString());
        List<GitCommitInfo> commits = gitService.getCommitsBetween(commit1, commit3);

        assertEquals(2, commits.size());

        GitCommitInfo commit2Info = commits.get(1);
        assertEquals("add b.txt", commit2Info.getFullMessage());
        assertEquals(1, commit2Info.getDiffEntries().size());
        GitDiffEntry commit2Diff = commit2Info.getDiffEntries().getFirst();
        assertEquals(ChangeOperation.ADD, commit2Diff.getChangeOperation());
        assertEquals("b.txt", commit2Diff.getNewPath());
        assertEquals("content B", commit2Diff.getNewContent());

        GitCommitInfo commit3Info = commits.getFirst();
        assertEquals("modify a.txt and delete b.txt", commit3Info.getFullMessage());
        assertEquals(2, commit3Info.getDiffEntries().size());

        for (GitDiffEntry diff : commit3Info.getDiffEntries()) {
            if ("a.txt".equals(diff.getNewPath())) {
                assertEquals(ChangeOperation.MODIFY, diff.getChangeOperation());
                assertEquals("original A", diff.getOldContent());
                assertEquals("modified A", diff.getNewContent());
            } else if (diff.getNewPath() == null) {
                assertEquals(ChangeOperation.REMOVE, diff.getChangeOperation());
                assertEquals("b.txt", diff.getOldPath());
                assertEquals("content B", diff.getOldContent());
            } else {
                fail("Unexpected diff entry: " + diff.getNewPath());
            }
        }
    }

    @Test
    @DisplayName("相同 hash 传入应返回空列表")
    void testGetCommitsBetweenSameHash() throws Exception {
        String commit = commitFile("test.txt", "test", "init");
        gitService = new GitService(tempDir.toString());

        List<GitCommitInfo> commits = gitService.getCommitsBetween(commit, commit);

        assertNotNull(commits);
        assertTrue(commits.isEmpty());
    }

    @Test
    @DisplayName("两个无效 hash 应抛出 IllegalArgumentException")
    void testGetCommitsBetweenBothInvalidHashes() throws Exception {
        commitFile("test.txt", "test", "init");
        gitService = new GitService(tempDir.toString());

        assertThrows(IllegalArgumentException.class,
                () -> gitService.getCommitsBetween("deadbeef", "cafebabe"));
    }

    @Test
    @DisplayName("一个有效一个无效 hash 应抛出 IllegalArgumentException")
    void testGetCommitsBetweenOneInvalidHash() throws Exception {
        String commit = commitFile("test.txt", "test", "init");
        gitService = new GitService(tempDir.toString());

        assertThrows(IllegalArgumentException.class,
                () -> gitService.getCommitsBetween(commit, "deadbeef"));

        assertThrows(IllegalArgumentException.class,
                () -> gitService.getCommitsBetween("deadbeef", commit));
    }

    @Test
    @DisplayName("fromHash 为初始提交时应排除初始提交，仅返回后续提交")
    void testGetCommitsBetweenExcludesFromHash() throws Exception {
        String commit1 = commitFile("a.txt", "content A", "initial commit");
        String commit2 = commitFile("b.txt", "content B", "second commit");

        gitService = new GitService(tempDir.toString());
        List<GitCommitInfo> commits = gitService.getCommitsBetween(commit1, commit2);

        assertEquals(1, commits.size());
        assertEquals(commit2, commits.getFirst().getCommitHash());
    }

    @Test
    @DisplayName("提交信息应包含正确的 author、email、timestamp 和 message")
    void testGetCommitsBetweenMetadataCorrect() throws Exception {
        String commit1 = commitFile("a.txt", "content", "first commit");
        String commit2 = commit("second commit");

        gitService = new GitService(tempDir.toString());
        List<GitCommitInfo> commits = gitService.getCommitsBetween(commit1, commit2);

        assertEquals(1, commits.size());
        GitCommitInfo info = commits.getFirst();

        assertEquals("Test User", info.getAuthorName());
        assertEquals("test@example.com", info.getAuthorEmail());
        assertTrue(info.getTimestamp() > 0);
        assertEquals("second commit", info.getFullMessage());
    }

    @Test
    @DisplayName("相邻提交之间 diff 应只包含该提交自身的变更")
    void testGetCommitsBetweenEachCommitOwnDiff() throws Exception {
        String commit1 = commitFile("a.txt", "A1", "first");
        String commit2 = commitFile("b.txt", "B1", "second");
        String commit3 = commitFile("c.txt", "C1", "third");
        String commit4 = commitFile("d.txt", "D1", "fourth");

        gitService = new GitService(tempDir.toString());
        List<GitCommitInfo> commits = gitService.getCommitsBetween(commit1, commit4);

        assertEquals(3, commits.size());

        GitCommitInfo c2 = commits.get(2);
        assertEquals(1, c2.getDiffEntries().size());
        assertEquals("b.txt", c2.getDiffEntries().getFirst().getNewPath());

        GitCommitInfo c3 = commits.get(1);
        assertEquals(1, c3.getDiffEntries().size());
        assertEquals("c.txt", c3.getDiffEntries().getFirst().getNewPath());

        GitCommitInfo c4 = commits.getFirst();
        assertEquals(1, c4.getDiffEntries().size());
        assertEquals("d.txt", c4.getDiffEntries().getFirst().getNewPath());
    }
}
