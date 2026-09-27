package com.github.semanticgit.git.service;

import com.github.semanticgit.common.entity.Author;
import com.github.semanticgit.common.entity.ChangeOperation;
import com.github.semanticgit.common.entity.CommitMeta;
import com.github.semanticgit.git.dto.GitCommitInfo;
import com.github.semanticgit.git.dto.GitDiffEntry;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;


public class GitService implements AutoCloseable, IGitService {

    private static final int BINARY_SNIFF_LEN = 8000;
    private static final long MAX_FILE_CONTENT_BYTES = 5L * 1024 * 1024;

    private final Repository repository;
    private final Git git;
    private final boolean ownsRepository;

    public GitService(String repoPath) throws IOException {
        this(buildRepository(repoPath), true);
    }

    /** 供测试/复用已有 Repository 使用。调用方负责关闭该 Repository */
    public GitService(Repository repository) {
        this(repository, false);
    }

    /** 私有主构造：统一初始化 */
    private GitService(Repository repository, boolean ownsRepository) {
        this.repository = repository;
        this.git = new Git(repository);
        this.ownsRepository = ownsRepository;
    }

    private static Repository buildRepository(String repoPath) throws IOException {
        File repoFile = new File(repoPath).getAbsoluteFile();
        File dotGit = new File(repoFile, ".git");
        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        if (dotGit.isDirectory()) {
            builder.setGitDir(dotGit).setWorkTree(repoFile);
        } else if (new File(repoFile, "HEAD").isFile()
                && new File(repoFile, "objects").isDirectory()) {
            builder.setGitDir(repoFile);
        } else {
            builder.findGitDir(repoFile);
        }
        return builder.readEnvironment().build();
    }

    // ==================== 分支 / Ref ====================

    /**
     * 按分支取提交列表（时间倒序，最新在前）。
     * <p><b>只沿 first-parent 链遍历</b>：merge commit 引入的 feature 分支提交
     * 不会出现在结果中。适用于"主线演进"视角。
     *
     * @param branch 短名（"main"）、完整 ref（"refs/heads/main"）或 null（等价 HEAD）
     */
    public List<CommitMeta> getAllCommits(String branch) throws IOException {
        List<CommitMeta> result = new ArrayList<>();

        ObjectId headId = resolveBranchOrHead(branch);
        if (headId == null) {
            return result; // 分支不存在，返回空表
        }

        Map<String, Author> authorCache = new HashMap<>();

        try (RevWalk walk = new RevWalk(repository)) {
            walk.setFirstParent(true);                    // ← 新增
            walk.markStart(walk.parseCommit(headId));
            for (RevCommit rev : walk) {
                PersonIdent ident = rev.getAuthorIdent();
                Author author = authorCache.computeIfAbsent(
                        ident.getEmailAddress(),
                        email -> Author.builder()
                                .name(ident.getName())
                                .email(email)
                                .build()
                );
                CommitMeta parent = null;
                if (rev.getParentCount() > 0) {
                    RevCommit p = rev.getParent(0);
                    PersonIdent pIdent = p.getAuthorIdent();
                    parent = CommitMeta.builder()
                            .hash(p.getId().getName())
                            .author(authorCache.computeIfAbsent(
                                    pIdent.getEmailAddress(),
                                    email -> Author.builder()
                                            .name(pIdent.getName())
                                            .email(email)
                                            .build()))
                            .timestamp(p.getCommitTime())
                            .message(p.getFullMessage())
                            .build();
                }
                result.add(CommitMeta.builder()
                        .hash(rev.getId().getName())
                        .author(author)
                        .timestamp(rev.getCommitTime())
                        .message(rev.getFullMessage())
                        .parentCommitMeta(parent)
                        .build());
            }
        }
        return result;
    }

    /** 兼容旧调用：默认 HEAD */
    public List<CommitMeta> getAllCommits() throws IOException {
        return getAllCommits(null);
    }

    /** 列出所有本地分支短名，按字典序 */
    public List<String> getBranches() throws IOException {
        List<String> result = new ArrayList<>();
        for (Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_HEADS)) {
            result.add(Repository.shortenRefName(ref.getName()));
        }
        Collections.sort(result);
        return result;
    }

    /** 通用 ref 解析。短名优先当本地分支，避免 tag 同名覆盖 */
    public @Nullable String resolveRef(String ref) throws IOException {
        if (ref == null || ref.isEmpty()) return null;

        if (!ref.contains("/") && !ref.contains("~") && !ref.contains("^")) {
            Ref branch = repository.exactRef(Constants.R_HEADS + ref);
            if (branch != null && branch.getObjectId() != null) {
                return branch.getObjectId().getName();
            }
        }
        ObjectId id = repository.resolve(ref);
        return id == null ? null : id.getName();
    }

    /** 当前 HEAD 指向的 commit hash。空仓库返回 null */
    public @Nullable String getHeadCommit() throws IOException {
        return resolveRef(Constants.HEAD);
    }

    /** 取指定本地分支 head。短名或 refs/heads/xxx 均可 */
    public @Nullable String getBranchHead(String branchName) throws IOException {
        if (branchName == null || branchName.isEmpty()) return null;
        String fullName = branchName.startsWith(Constants.R_HEADS)
                ? branchName
                : Constants.R_HEADS + branchName;

        Ref ref = repository.exactRef(fullName);
        return (ref == null || ref.getObjectId() == null)
                ? null
                : ref.getObjectId().getName();
    }

    /** 所有本地分支 head。key=短名，value=commit hash */
    public Map<String, String> getAllBranchHeads() throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_HEADS)) {
            ObjectId id = ref.getObjectId();
            if (id != null) {
                result.put(Repository.shortenRefName(ref.getName()), id.getName());
            }
        }
        return result;
    }

    /** 探测默认分支名。优先级：origin/HEAD → main → master → 唯一本地分支 */
    public @Nullable String getDefaultBranch() throws IOException {
        Ref originHead = repository.exactRef("refs/remotes/origin/HEAD");
        if (originHead != null && originHead.isSymbolic()) {
            return Repository.shortenRefName(originHead.getTarget().getName())
                    .replaceFirst("^origin/", "");
        }
        for (String candidate : new String[]{"main", "master"}) {
            if (repository.exactRef(Constants.R_HEADS + candidate) != null) {
                return candidate;
            }
        }
        Map<String, String> locals = getAllBranchHeads();
        return locals.size() == 1 ? locals.keySet().iterator().next() : null;
    }

    // ==================== diff ====================

    /**
     * 两个 ref/commit 之间的文件级快照对比。
     */
    public List<GitDiffEntry> getDiffBetweenCommits(String fromRef, String toRef)
            throws IOException, IllegalArgumentException {
        ObjectId fromId = repository.resolve(fromRef);
        ObjectId toId = repository.resolve(toRef);
        if (fromId == null || toId == null) {
            throw new IllegalArgumentException(
                    "Unresolvable ref: " + (fromId == null ? fromRef : toRef));
        }
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit from = walk.parseCommit(fromId);
            RevCommit to = walk.parseCommit(toId);
            return diffTrees(from.getTree().getId(), to.getTree().getId());
        }
    }

    // ==================== getCommitInfo ====================

    /**
     * 单个提交完整信息（含 diff）。
     * <p>⚠️ merge commit 的 {@code diffEntries} 只相对 <b>first parent</b>。
     */
    public GitCommitInfo getCommitInfo(String ref) throws IOException, IllegalArgumentException {
        ObjectId commitId = repository.resolve(ref);
        if (commitId == null) {
            throw new IllegalArgumentException("Unresolvable ref: " + ref);
        }
        try (RevWalk walk = new RevWalk(repository)) {
            return buildCommitInfo(walk.parseCommit(commitId), walk);
        }
    }



    /**
     * 计算某提交相对其所有父提交的 diff。root 返回空 Map。
     */
    public Map<String, List<GitDiffEntry>> getDiffsForAllParents(String ref)
            throws IOException, IllegalArgumentException {
        ObjectId commitId = repository.resolve(ref);
        if (commitId == null) {
            throw new IllegalArgumentException("Unresolvable ref: " + ref);
        }
        Map<String, List<GitDiffEntry>> result = new LinkedHashMap<>();
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit rev = walk.parseCommit(commitId);
            if (rev.getParentCount() == 0) return result;

            ObjectId currentTree = rev.getTree().getId();
            for (RevCommit parent : rev.getParents()) {
                RevCommit parsed = walk.parseCommit(parent.getId());
                result.put(parsed.getId().getName(),
                        diffTrees(parsed.getTree().getId(), currentTree));
            }
        }
        return result;
    }

    // ==================== getCommitsBetween ====================

    /**
     * fromRef（不含）到 toRef（含）之间的逐提交列表，<b>只走 first-parent 链</b>。
     * 要求 fromRef 是 toRef 的祖先，否则抛异常。
     * <p>merge commit 引入的 feature 分支提交不会出现在结果中。
     */
    public List<GitCommitInfo> getCommitsBetween(String fromRef, String toRef)
            throws IOException, IllegalArgumentException {
        ObjectId fromId = repository.resolve(fromRef);
        ObjectId toId = repository.resolve(toRef);
        if (fromId == null || toId == null) {
            throw new IllegalArgumentException(
                    "Unresolvable ref: " + (fromId == null ? fromRef : toRef));
        }

        // 1) 祖先校验用独立的 RevWalk，避免污染后续遍历
        try (RevWalk checkWalk = new RevWalk(repository)) {
            RevCommit from = checkWalk.parseCommit(fromId);
            RevCommit to = checkWalk.parseCommit(toId);
            if (!checkWalk.isMergedInto(from, to)) {
                throw new IllegalArgumentException(
                        fromRef + " is not an ancestor of " + toRef);
            }
        }

        // 2) 遍历用新的 RevWalk
        List<GitCommitInfo> result = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repository)) {
            walk.setFirstParent(true);                    // ← 新增
            RevCommit from = walk.parseCommit(fromId);
            RevCommit to = walk.parseCommit(toId);
            walk.markStart(to);
            walk.markUninteresting(from);
            for (RevCommit rev : walk) {
                result.add(buildCommitInfo(rev, walk));
            }
        }
        return result;
    }

    // ==================== count / forEach ====================

    /**
     * 统计两个 ref 之间的提交数量（不含 fromRef，含 toRef）。
     * 要求 fromRef 是 toRef 的祖先，否则抛异常。
     *
     * <p>只遍历计数，不计算 diff、不构建 DTO，
     * 比 {@code getCommitsBetween(a, b).size()} 快一个数量级。
     *
     * @param fromRef 起始 ref（不含）
     * @param toRef   结束 ref（含）
     * @return 提交数量
     * @throws IOException               IO 失败
     * @throws IllegalArgumentException  ref 无法解析，或 fromRef 不是 toRef 的祖先
     */
    @Override
    public int countCommitsBetween(String fromRef, String toRef) throws IOException {
        ObjectId fromId = repository.resolve(fromRef);
        ObjectId toId = repository.resolve(toRef);
        if (fromId == null || toId == null) {
            throw new IllegalArgumentException(
                    "Unresolvable ref: " + (fromId == null ? fromRef : toRef));
        }

        try (RevWalk checkWalk = new RevWalk(repository)) {
            RevCommit from = checkWalk.parseCommit(fromId);
            RevCommit to = checkWalk.parseCommit(toId);
            if (!checkWalk.isMergedInto(from, to)) {
                throw new IllegalArgumentException(
                        fromRef + " is not an ancestor of " + toRef);
            }
        }

        int count = 0;
        try (RevWalk walk = new RevWalk(repository)) {
            walk.setFirstParent(true);                    // ← 新增
            RevCommit from = walk.parseCommit(fromId);
            RevCommit to = walk.parseCommit(toId);
            walk.markStart(to);
            walk.markUninteresting(from);
            for (RevCommit ignored : walk) {
                count++;
            }
        }
        return count;
    }

    /**
     * 遍历两个 ref 之间的所有提交（不含 fromRef，含 toRef），时间倒序。
     * 要求 fromRef 是 toRef 的祖先，否则抛异常。
     *
     * <p>流式处理，不累积列表；consumer 抛异常会中断遍历并向上传播。
     *
     * @param fromRef  起始 ref（不含）
     * @param toRef    结束 ref（含）
     * @param consumer 每个提交信息的消费函数
     * @throws IOException               IO 失败
     * @throws IllegalArgumentException  ref 无法解析，或 fromRef 不是 toRef 的祖先
     */
    @Override
    public void forEachCommitBetween(String fromRef, String toRef,
                                     Consumer<GitCommitInfo> consumer) throws IOException {
        ObjectId fromId = repository.resolve(fromRef);
        ObjectId toId = repository.resolve(toRef);
        if (fromId == null || toId == null) {
            throw new IllegalArgumentException(
                    "Unresolvable ref: " + (fromId == null ? fromRef : toRef));
        }

        try (RevWalk checkWalk = new RevWalk(repository)) {
            RevCommit from = checkWalk.parseCommit(fromId);
            RevCommit to = checkWalk.parseCommit(toId);
            if (!checkWalk.isMergedInto(from, to)) {
                throw new IllegalArgumentException(
                        fromRef + " is not an ancestor of " + toRef);
            }
        }

        try (RevWalk walk = new RevWalk(repository)) {
            walk.setFirstParent(true);                    // ← 新增
            RevCommit from = walk.parseCommit(fromId);
            RevCommit to = walk.parseCommit(toId);
            walk.markStart(to);
            walk.markUninteresting(from);
            for (RevCommit rev : walk) {
                consumer.accept(buildCommitInfo(rev, walk));
            }
        }
    }

    // ==================== 内部 ====================

    private ObjectId resolveBranchOrHead(String branch) throws IOException {
        if (branch == null || branch.isEmpty() || Constants.HEAD.equals(branch)) {
            return repository.resolve(Constants.HEAD);
        }
        String fullRef = branch.startsWith(Constants.R_HEADS)
                ? branch
                : Constants.R_HEADS + branch;
        Ref ref = repository.exactRef(fullRef);
        return (ref == null) ? repository.resolve(branch) : ref.getObjectId();
    }



    private GitCommitInfo buildCommitInfo(RevCommit rev, RevWalk walk) throws IOException {
        GitCommitInfo info = new GitCommitInfo();
        info.setCommitHash(rev.getId().getName());
        info.setAuthorName(rev.getAuthorIdent().getName());
        info.setAuthorEmail(rev.getAuthorIdent().getEmailAddress());
        info.setTimestamp(rev.getCommitTime());
        info.setFullMessage(rev.getFullMessage());

        int parentCount = rev.getParentCount();
        info.setParentCount(parentCount);
        info.setMergeCommit(parentCount >= 2);

        if (parentCount > 0) {
            List<String> parentHashes = new ArrayList<>(parentCount);
            for (RevCommit p : rev.getParents()) {
                parentHashes.add(p.getId().getName());
            }
            info.setParentHashes(parentHashes);

            RevCommit firstParent = walk.parseCommit(rev.getParent(0).getId());
            info.setDiffBaseParentHash(firstParent.getId().getName());
            info.setDiffEntries(diffTrees(
                    firstParent.getTree().getId(),
                    rev.getTree().getId()));
        } else {
            info.setParentHashes(List.of());
            info.setDiffBaseParentHash(null);
            info.setDiffEntries(getAllFilesAsAdd(rev.getTree().getId()));
        }
        return info;
    }

    private List<GitDiffEntry> diffTrees(ObjectId fromTree, ObjectId toTree) throws IOException {
        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(repository);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            df.setDetectRenames(true);

            List<DiffEntry> entries = df.scan(fromTree, toTree);
            List<GitDiffEntry> result = new ArrayList<>(entries.size());
            for (DiffEntry entry : entries) {
                result.add(toGitDiffEntry(entry, fromTree, toTree));
            }
            return result;
        }
    }

    private GitDiffEntry toGitDiffEntry(DiffEntry entry, ObjectId fromTree, ObjectId toTree)
            throws IOException {
        GitDiffEntry dto = new GitDiffEntry();
        dto.setChangeOperation(mapChangeType(entry.getChangeType()));

        boolean isAdd = entry.getChangeType() == DiffEntry.ChangeType.ADD;
        boolean isDelete = entry.getChangeType() == DiffEntry.ChangeType.DELETE;

        dto.setOldPath(isAdd ? null : entry.getOldPath());
        dto.setNewPath(isDelete ? null : entry.getNewPath());

        if (!isDelete && dto.getNewPath() != null) {
            dto.setNewContent(readFileContent(toTree, dto.getNewPath()));
        }
        if (!isAdd && dto.getOldPath() != null) {
            dto.setOldContent(readFileContent(fromTree, dto.getOldPath()));
        }
        return dto;
    }

    private @Nullable String readFileContent(ObjectId treeId, String filePath) throws IOException {
        if (filePath == null || DiffEntry.DEV_NULL.equals(filePath)) return null;

        try (TreeWalk tw = TreeWalk.forPath(repository, filePath, treeId)) {
            if (tw == null) return null;

            if (tw.getFileMode(0) == org.eclipse.jgit.lib.FileMode.GITLINK) {
                return null;
            }
            ObjectId blobId = tw.getObjectId(0);
            ObjectLoader loader = repository.open(blobId);

            if (loader.getSize() > MAX_FILE_CONTENT_BYTES) return null;
            byte[] bytes = loader.getBytes();
            if (isBinary(bytes)) return null;
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static boolean isBinary(byte[] bytes) {
        int len = Math.min(bytes.length, BINARY_SNIFF_LEN);
        for (int i = 0; i < len; i++) {
            if (bytes[i] == 0) return true;
        }
        return false;
    }

    private List<GitDiffEntry> getAllFilesAsAdd(ObjectId treeId) throws IOException {
        List<GitDiffEntry> result = new ArrayList<>();
        try (TreeWalk tw = new TreeWalk(repository)) {
            tw.addTree(treeId);
            tw.setRecursive(true);
            while (tw.next()) {
                if (tw.getFileMode(0) == org.eclipse.jgit.lib.FileMode.GITLINK) continue;

                GitDiffEntry dto = new GitDiffEntry();
                dto.setChangeOperation(ChangeOperation.ADD);
                dto.setOldPath(null);
                dto.setNewPath(tw.getPathString());
                dto.setOldContent(null);
                dto.setNewContent(readFileContent(treeId, tw.getPathString()));
                result.add(dto);
            }
        }
        return result;
    }

    @Override
    public void close() {
        if (git != null) git.close();
        if (ownsRepository && repository != null) {
            repository.close();
        }
    }

    private ChangeOperation mapChangeType(DiffEntry.@NonNull ChangeType type) {
        return switch (type) {
            case ADD -> ChangeOperation.ADD;
            case DELETE -> ChangeOperation.REMOVE;
            case MODIFY, COPY, RENAME -> ChangeOperation.MODIFY;
        };
    }
}
