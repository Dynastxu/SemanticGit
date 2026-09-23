package com.github.semanticgit.git.service;

import com.github.semanticgit.common.entity.Author;
import com.github.semanticgit.common.entity.ChangeOperation;
import com.github.semanticgit.common.entity.CommitMeta;
import com.github.semanticgit.git.dto.GitCommitInfo;
import com.github.semanticgit.git.dto.GitDiffEntry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GitService")
class GitServiceTest {

    @TempDir
    Path tempDir;

    private TestRepo repo;
    private GitService gitService;

    @BeforeEach
    void setUp() throws Exception {
        repo = TestRepo.init(tempDir);
    }

    @AfterEach
    void tearDown() {
        if (gitService != null) gitService.close();
        if (repo != null) repo.close();
    }

    private GitService service() throws Exception {
        if (gitService == null) {
            gitService = new GitService(repo.repository());   // ← 用 TestRepo 的 Repository
        }
        return gitService;
    }

    private static Map<String, GitDiffEntry> byNewPath(List<GitDiffEntry> diffs) {
        return diffs.stream()
                .filter(e -> e.getNewPath() != null)
                .collect(Collectors.toMap(GitDiffEntry::getNewPath, Function.identity()));
    }

    // ==================== 构造 ====================

    @Nested
    @DisplayName("构造")
    class Constructor {
        @Test void workspaceRoot() throws Exception {
            repo.commitFile("test.txt", "test", "init");
            assertDoesNotThrow(() -> new GitService(tempDir.toString()));
        }

        @Test void dotGitDir() throws Exception {
            repo.commitFile("test.txt", "test", "init");
            assertDoesNotThrow(() -> new GitService(tempDir.resolve(".git").toString()));
        }

        @Test void emptyRepo() {
            assertDoesNotThrow(() -> new GitService(tempDir.toString()));
        }
    }

    // ==================== getDiffBetweenCommits ====================

    @Nested
    @DisplayName("getDiffBetweenCommits")
    class Diff {

        @Test
        @DisplayName("ADD / MODIFY / DELETE 全场景")
        void mixed() throws Exception {
            repo.commitFile("a.txt", "content A", "first");
            String c1 = repo.commitFile("b.txt", "content B", "add b");

            repo.writeFile("a.txt", "modified A");
            repo.stage("a.txt");
            repo.commitFile("c.txt", "content C", "add c");
            repo.rm("b.txt");
            String c2 = repo.commit("mixed");

            List<GitDiffEntry> diffs = service().getDiffBetweenCommits(c1, c2);
            assertEquals(3, diffs.size());

            GitDiffEntry a = byNewPath(diffs).get("a.txt");
            assertAll(
                    () -> assertEquals(ChangeOperation.MODIFY, a.getChangeOperation()),
                    () -> assertEquals("content A", a.getOldContent()),
                    () -> assertEquals("modified A", a.getNewContent())
            );

            GitDiffEntry c = byNewPath(diffs).get("c.txt");
            assertAll(
                    () -> assertEquals(ChangeOperation.ADD, c.getChangeOperation()),
                    () -> assertNull(c.getOldContent()),
                    () -> assertEquals("content C", c.getNewContent())
            );

            GitDiffEntry b = diffs.stream()
                    .filter(e -> e.getNewPath() == null).findFirst().orElseThrow();
            assertAll(
                    () -> assertEquals(ChangeOperation.REMOVE, b.getChangeOperation()),
                    () -> assertEquals("b.txt", b.getOldPath()),
                    () -> assertEquals("content B", b.getOldContent())
            );
        }

        @Test void sameCommit() throws Exception {
            String h = repo.commitFile("test.txt", "test", "init");
            assertTrue(service().getDiffBetweenCommits(h, h).isEmpty());
        }

        @Test void rename() throws Exception {
            String c1 = repo.commitFile("old.txt", "same", "init");
            Files.move(tempDir.resolve("old.txt"), tempDir.resolve("new.txt"));
            repo.stage("old.txt");
            repo.stage("new.txt");
            String c2 = repo.commit("rename");

            List<GitDiffEntry> diffs = service().getDiffBetweenCommits(c1, c2);
            GitDiffEntry r = diffs.stream()
                    .filter(e -> "new.txt".equals(e.getNewPath())
                            && "old.txt".equals(e.getOldPath()))
                    .findFirst().orElseThrow(() -> new AssertionError("not detected: " + diffs));
            assertEquals("same", r.getNewContent());
        }

        @Test void noChanges() throws Exception {
            String c1 = repo.commitFile("same.txt", "x", "init");
            String c2 = repo.commit("empty");
            assertTrue(service().getDiffBetweenCommits(c1, c2).isEmpty());
        }

        @Test void invalid() throws Exception {
            String h = repo.commitFile("test.txt", "test", "init");
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service().getDiffBetweenCommits("deadbeef", "cafebabe")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service().getDiffBetweenCommits(h, "deadbeef")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service().getDiffBetweenCommits("deadbeef", h))
            );
        }
    }

    // ==================== getCommitInfo ====================

    @Nested
    @DisplayName("getCommitInfo")
    class CommitInfo {

        @Test void normal() throws Exception {
            repo.commitFile("a.txt", "A1", "first");
            String h = repo.commitFile("b.txt", "B1", "second");

            GitCommitInfo info = service().getCommitInfo(h);
            assertAll(
                    () -> assertEquals(h, info.getCommitHash()),
                    () -> assertEquals("Test User", info.getAuthorName()),
                    () -> assertEquals("test@example.com", info.getAuthorEmail()),
                    () -> assertEquals("second", info.getFullMessage()),
                    () -> assertTrue(info.getTimestamp() > 0),
                    () -> assertEquals(1, info.getParentCount()),
                    () -> assertFalse(info.isMergeCommit()),
                    () -> assertEquals(1, info.getParentHashes().size()),
                    () -> assertEquals(1, info.getDiffEntries().size())
            );
        }

        @Test void rootCommit() throws Exception {
            repo.writeFile("a.txt", "A");
            repo.writeFile("nested/b.txt", "B");
            repo.stage("a.txt");
            repo.stage("nested/b.txt");
            String h = repo.commit("initial");

            GitCommitInfo info = service().getCommitInfo(h);
            assertAll(
                    () -> assertEquals(0, info.getParentCount()),
                    () -> assertFalse(info.isMergeCommit()),
                    () -> assertTrue(info.getParentHashes().isEmpty()),
                    () -> assertNull(info.getDiffBaseParentHash()),
                    () -> assertEquals(2, info.getDiffEntries().size())
            );
        }

        @Test void invalid() throws Exception {
            repo.commitFile("test.txt", "test", "init");
            assertThrows(IllegalArgumentException.class,
                    () -> service().getCommitInfo("deadbeef"));
        }
    }

    // ==================== Merge Commit ====================

    @Nested
    @DisplayName("Merge Commit")
    class Merge {

        @Test
        @DisplayName("merge commit 暴露所有父提交，diff 相对 first parent")
        void mergeExposesParents() throws Exception {
            repo.commitFile("a.txt", "A", "initial");
            String defaultBranch = service().getDefaultBranch();

            repo.raw().checkout().setCreateBranch(true).setName("feature").call();
            repo.commitFile("b.txt", "B", "feature work");

            repo.raw().checkout().setName(defaultBranch).call();
            repo.commitFile("c.txt", "C", "main work");

            repo.raw().merge()
                    .include(repo.raw().getRepository().resolve("feature"))
                    .setCommit(true)
                    .setMessage("merge feature")
                    .call();

            String mergeHash = repo.head();
            GitService s = service();
            GitCommitInfo info = s.getCommitInfo(mergeHash);

            assertAll(
                    () -> assertTrue(info.isMergeCommit()),
                    () -> assertEquals(2, info.getParentCount()),
                    () -> assertEquals(2, info.getParentHashes().size()),
                    () -> assertEquals(info.getParentHashes().get(0),
                            info.getDiffBaseParentHash())
            );

            Map<String, List<GitDiffEntry>> all = s.getDiffsForAllParents(mergeHash);
            assertEquals(2, all.size());

            List<GitDiffEntry> vsFeature = all.get(info.getParentHashes().get(1));
            assertTrue(vsFeature.stream().anyMatch(d -> "c.txt".equals(d.getNewPath())));
        }

        @Test
        @DisplayName("root 提交 getDiffsForAllParents 返回空 Map")
        void rootAllParentsEmpty() throws Exception {
            String h = repo.commitFile("a.txt", "A", "init");
            assertTrue(service().getDiffsForAllParents(h).isEmpty());
        }
    }

    // ==================== getCommitsBetween ====================

    @Nested
    @DisplayName("getCommitsBetween")
    class Between {

        @Test void multiple() throws Exception {
            String c1 = repo.commitFile("a.txt", "A", "first");
            String c2 = repo.commitFile("b.txt", "B", "second");
            String c3 = repo.commitFile("c.txt", "C", "third");

            List<GitCommitInfo> commits = service().getCommitsBetween(c1, c3);
            assertEquals(2, commits.size());
            assertAll(
                    () -> assertEquals(c3, commits.get(0).getCommitHash()),
                    () -> assertEquals(c2, commits.get(1).getCommitHash())
            );
        }

        @Test void sameHash() throws Exception {
            String h = repo.commitFile("test.txt", "test", "init");
            assertTrue(service().getCommitsBetween(h, h).isEmpty());
        }

        @Test void notAncestor() throws Exception {
            repo.commitFile("a.txt", "A", "first");
            repo.raw().checkout().setCreateBranch(true).setName("feature").call();
            String featureTip = repo.commitFile("b.txt", "B", "feature");
            repo.raw().checkout().setName(service().getDefaultBranch()).call();
            String mainTip = repo.commitFile("c.txt", "C", "main");

            assertThrows(IllegalArgumentException.class,
                    () -> service().getCommitsBetween(mainTip, featureTip));
        }

        @Test void invalid() throws Exception {
            String h = repo.commitFile("test.txt", "test", "init");
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service().getCommitsBetween("deadbeef", "cafebabe")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service().getCommitsBetween(h, "deadbeef"))
            );
        }
    }

    // ==================== getAllCommits ====================

    @Nested
    @DisplayName("getAllCommits")
    class AllCommits {

        @Test void empty() {
            assertDoesNotThrow(() -> assertTrue(service().getAllCommits().isEmpty()));
        }

        @Test void single() throws Exception {
            String h = repo.commitFile("test.txt", "test", "single");
            List<CommitMeta> commits = service().getAllCommits();

            assertEquals(1, commits.size());
            CommitMeta meta = commits.getFirst();
            assertAll(
                    () -> assertEquals(h, meta.getHash()),
                    () -> assertEquals("single", meta.getMessage()),
                    () -> assertEquals("Test User", meta.getAuthor().getName()),
                    () -> assertEquals("test@example.com", meta.getAuthor().getEmail()),
                    () -> assertTrue(meta.getTimestamp() > 0)
            );
        }

        @Test void authorDedup() throws Exception {
            repo.commitFile("a.txt", "A", "first");
            repo.commitFile("b.txt", "B", "second");
            List<CommitMeta> commits = service().getAllCommits();

            assertSame(commits.get(0).getAuthor(), commits.get(1).getAuthor());
        }

        @Test void differentAuthorsNotMerged() throws Exception {
            repo.commitFile("a.txt", "A", "first");
            repo.writeFile("b.txt", "B");
            repo.stage("b.txt");
            repo.commitAs("Other", "other@example.com", "second");

            List<CommitMeta> commits = service().getAllCommits();
            assertNotSame(commits.get(0).getAuthor(), commits.get(1).getAuthor());
        }

        @Test
        @DisplayName("按分支取提交（包含所有可达历史）")
        void byBranch() throws Exception {
            repo.commitFile("a.txt", "A", "first");            // main: A
            String mainTip = repo.head();

            repo.raw().checkout().setCreateBranch(true).setName("feature").call();
            repo.commitFile("b.txt", "B", "feature work");     // feature: A → B

            GitService s = service();
            List<CommitMeta> featureCommits = s.getAllCommits("feature");
            List<CommitMeta> mainCommits = s.getAllCommits(s.getDefaultBranch());

            assertAll(
                    () -> assertEquals(2, featureCommits.size()),   // feature: A → B
                    () -> assertEquals(1, mainCommits.size()),      // main: 只有 A
                    () -> assertEquals(mainTip, mainCommits.getFirst().getHash())
            );
        }

        @Test
        @DisplayName("不存在的分支返回空列表")
        void nonexistentBranch() throws Exception {
            repo.commitFile("a.txt", "A", "first");
            assertTrue(service().getAllCommits("no-such-branch").isEmpty());
        }
    }

    // ==================== 分支与 Ref ====================

    @Nested
    @DisplayName("分支与 Ref")
    class Refs {

        @Test void branchHead() throws Exception {
            repo.commitFile("a.txt", "A", "first");
            String h = repo.commitFile("b.txt", "B", "second");
            assertEquals(h, service().getBranchHead(service().getDefaultBranch()));
            assertEquals(h, service().getHeadCommit());
        }

        @Test void fullName() throws Exception {
            repo.commitFile("a.txt", "A", "first");
            String h = repo.head();
            assertEquals(h, service().getBranchHead(
                    "refs/heads/" + service().getDefaultBranch()));
        }

        @Test void notFound() throws Exception {
            repo.commitFile("a.txt", "A", "first");
            assertNull(service().getBranchHead("no-such-branch"));
        }

        @Test void multipleBranches() throws Exception {
            repo.commitFile("a.txt", "A", "first");
            String mainTip = repo.head();

            repo.raw().checkout().setCreateBranch(true).setName("feature").call();
            String featureTip = repo.commitFile("b.txt", "B", "feature");

            Map<String, String> heads = service().getAllBranchHeads();
            assertAll(
                    () -> assertEquals(2, heads.size()),
                    () -> assertEquals(featureTip, heads.get("feature")),
                    () -> assertEquals(mainTip, heads.get(service().getDefaultBranch()))
            );
        }

        @Test void listBranches() throws Exception {
            repo.commitFile("a.txt", "A", "first");
            repo.raw().branchCreate().setName("feature").call();

            List<String> branches = service().getBranches();
            assertTrue(branches.contains("feature"));
            assertTrue(branches.contains(service().getDefaultBranch()));
        }

        @Test void defaultBranch() throws Exception {
            repo.commitFile("a.txt", "A", "first");
            String def = service().getDefaultBranch();
            assertNotNull(def);
            assertTrue(Set.of("main", "master").contains(def));
        }

        @Test void invalidRef() throws Exception {
            repo.commitFile("a.txt", "A", "first");
            assertNull(service().resolveRef("no-such-ref"));
        }
    }

    // ==================== 资源管理 ====================

    @Nested
    @DisplayName("资源管理")
    class Resources {
        @Test void close() throws Exception {
            repo.commitFile("test.txt", "test", "init");
            GitService s = new GitService(tempDir.toString());
            assertDoesNotThrow(s::close);
        }

        @Test void closeIdempotent() throws Exception {
            repo.commitFile("test.txt", "test", "init");
            GitService s = new GitService(tempDir.toString());
            s.close();
            assertDoesNotThrow(s::close);
        }
    }

    // ==================== 边界 ====================

    @Nested
    @DisplayName("边界")
    class Edge {

        @Test void emptyFile() throws Exception {
            String c1 = repo.commitFile("empty.txt", "", "init");
            repo.writeFile("empty.txt", "now has content");
            repo.stage("empty.txt");
            String c2 = repo.commit("modify");

            GitDiffEntry d = service().getDiffBetweenCommits(c1, c2).getFirst();
            assertAll(
                    () -> assertEquals("", d.getOldContent()),
                    () -> assertEquals("now has content", d.getNewContent())
            );
        }

        @Test void binaryReturnsNull() throws Exception {
            byte[] binary = {0x00, 0x01, 0x02, (byte) 0xFF, 0x00, 0x42};
            String c1 = repo.commitFile("a.txt", "text", "init");
            repo.writeBytes("blob.bin", binary);
            repo.stage("blob.bin");
            String c2 = repo.commit("add binary");

            GitDiffEntry bin = byNewPath(service().getDiffBetweenCommits(c1, c2)).get("blob.bin");
            assertNotNull(bin);
            assertNull(bin.getNewContent());
        }

        @Test void largeFileReturnsNull() throws Exception {
            byte[] big = new byte[6 * 1024 * 1024];
            Arrays.fill(big, (byte) 'x');

            String c1 = repo.commitFile("a.txt", "text", "init");
            repo.writeBytes("big.txt", big);
            repo.stage("big.txt");
            String c2 = repo.commit("add big");

            GitDiffEntry e = byNewPath(service().getDiffBetweenCommits(c1, c2)).get("big.txt");
            assertNotNull(e);
            assertNull(e.getNewContent());
        }
    }
    @Nested
    @DisplayName("countCommitsBetween / forEachCommitBetween")
    class CountAndForEach {

        @Test
        @DisplayName("count 应返回正确数量")
        void count() throws Exception {
            String c1 = repo.commitFile("a.txt", "A", "first");
            repo.commitFile("b.txt", "B", "second");
            repo.commitFile("c.txt", "C", "third");
            String c4 = repo.commitFile("d.txt", "D", "fourth");

            assertEquals(3, service().countCommitsBetween(c1, c4));
        }

        @Test
        @DisplayName("from == to 时 count 为 0")
        void countSameHash() throws Exception {
            String h = repo.commitFile("test.txt", "test", "init");
            assertEquals(0, service().countCommitsBetween(h, h));
        }

        @Test
        @DisplayName("count 与 getCommitsBetween().size() 一致")
        void countMatchesList() throws Exception {
            String c1 = repo.commitFile("a.txt", "A", "first");
            repo.commitFile("b.txt", "B", "second");
            String c3 = repo.commitFile("c.txt", "C", "third");

            GitService s = service();
            assertEquals(s.getCommitsBetween(c1, c3).size(),
                    s.countCommitsBetween(c1, c3));
        }

        @Test
        @DisplayName("forEach 应按时间倒序逐个回调")
        void forEachOrder() throws Exception {
            String c1 = repo.commitFile("a.txt", "A", "first");
            String c2 = repo.commitFile("b.txt", "B", "second");
            String c3 = repo.commitFile("c.txt", "C", "third");

            List<String> hashes = new ArrayList<>();
            service().forEachCommitBetween(c1, c3, info -> hashes.add(info.getCommitHash()));

            assertEquals(List.of(c3, c2), hashes);
        }

        @Test
        @DisplayName("forEach 与 getCommitsBetween 结果一致")
        void forEachMatchesList() throws Exception {
            String c1 = repo.commitFile("a.txt", "A", "first");
            repo.commitFile("b.txt", "B", "second");
            String c3 = repo.commitFile("c.txt", "C", "third");

            GitService s = service();
            List<String> fromList = s.getCommitsBetween(c1, c3).stream()
                    .map(GitCommitInfo::getCommitHash)
                    .toList();

            List<String> fromForEach = new ArrayList<>();
            s.forEachCommitBetween(c1, c3, info -> fromForEach.add(info.getCommitHash()));

            assertEquals(fromList, fromForEach);
        }

        @Test
        @DisplayName("非祖先关系应抛异常")
        void notAncestor() throws Exception {
            repo.commitFile("a.txt", "A", "first");
            repo.raw().checkout().setCreateBranch(true).setName("feature").call();
            String featureTip = repo.commitFile("b.txt", "B", "feature");
            repo.raw().checkout().setName(service().getDefaultBranch()).call();
            String mainTip = repo.commitFile("c.txt", "C", "main");

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service().countCommitsBetween(mainTip, featureTip)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service().forEachCommitBetween(mainTip, featureTip, info -> {}))
            );
        }

        @Test
        @DisplayName("无效 ref 应抛异常")
        void invalidRef() throws Exception {
            String h = repo.commitFile("test.txt", "test", "init");
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service().countCommitsBetween(h, "deadbeef")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service().forEachCommitBetween("deadbeef", h, info -> {}))
            );
        }
    }
}