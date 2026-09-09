package com.github.semanticgit.git.service;

import com.github.semanticgit.common.entity.Author;
import com.github.semanticgit.common.entity.CommitMeta;
import com.github.semanticgit.git.dto.GitCommitInfo;
import com.github.semanticgit.git.dto.GitDiffEntry;
import com.github.semanticgit.common.entity.ChangeOperation;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GitService implements AutoCloseable {
    private final Repository repository;
    private final Git git;

    public GitService(String repoPath) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        // 兼容 .git 文件夹或工作区根目录
        this.repository = builder.setGitDir(new File(repoPath + "/.git"))
                .readEnvironment()
                .findGitDir()
                .build();
        this.git = new Git(repository);
    }

    public List<CommitMeta> getAllCommits() throws IOException {
        List<CommitMeta> result = new ArrayList<>();
        List<Author> authors = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repository)) {
            ObjectId headId = repository.resolve("HEAD");
            if (headId == null) {
                return result;
            }
            walk.markStart(walk.parseCommit(headId));
            for (RevCommit rev : walk) {
                Author author = Author.builder()
                        .name(rev.getAuthorIdent().getName())
                        .email(rev.getAuthorIdent().getEmailAddress())
                        .build();
                if (!authors.contains(author)) {
                    authors.add(author);
                } else {
                    author = authors.get(authors.indexOf(author));
                }
                CommitMeta meta = CommitMeta.builder()
                        .hash(rev.getId().getName())
                        .author(author)
                        .timestamp(rev.getCommitTime())
                        .message(rev.getFullMessage())
                        .build();
                result.add(meta);
            }
        }
        return result;
    }

    /**
     * 获取两个提交之间的文件级快照对比（用于 AB 兜底）
     */
    public List<GitDiffEntry> getDiffBetweenCommits(String fromHash, String toHash) throws IOException, IllegalArgumentException {
        ObjectId fromId = repository.resolve(fromHash + "^{tree}");
        ObjectId toId = repository.resolve(toHash + "^{tree}");

        if (fromId == null || toId == null) {
            throw new IllegalArgumentException("Invalid hash");
        }

        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(repository);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            df.setDetectRenames(true); // 开启重命名检测

            List<DiffEntry> entries = df.scan(fromId, toId);
            List<GitDiffEntry> result = new ArrayList<>();
            for (DiffEntry entry : entries) {
                GitDiffEntry dto = new GitDiffEntry();
                dto.setChangeOperation(mapChangeType(entry.getChangeType()));
                dto.setOldPath(DiffEntry.DEV_NULL.equals(entry.getOldPath()) ? null : entry.getOldPath());
                dto.setNewPath(DiffEntry.DEV_NULL.equals(entry.getNewPath()) ? null : entry.getNewPath());

                // 一次性把文件内容读出来，方便后续 Parser 直接解析
                // TODO 内存优化
                if (entry.getChangeType() != DiffEntry.ChangeType.DELETE) {
                    dto.setNewContent(getFileContent(toHash, entry.getNewPath()));
                }
                if (entry.getChangeType() != DiffEntry.ChangeType.ADD) {
                    dto.setOldContent(getFileContent(fromHash, entry.getOldPath()));
                }
                result.add(dto);
            }
            return result;
        }
    }

    /**
     * 获取单个提交的完整信息（含 diff），用于初始提交等场景
     */
    public GitCommitInfo getCommitInfo(String hash) throws IOException, IllegalArgumentException {
        ObjectId commitId = repository.resolve(hash);
        if (commitId == null) {
            throw new IllegalArgumentException("Invalid hash: " + hash);
        }
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit rev = walk.parseCommit(commitId);
            return buildCommitInfo(rev);
        }
    }

    /**
     * 获取两个提交之间的逐提交列表（不含 fromHash，用于增量分析）
     */
    public List<GitCommitInfo> getCommitsBetween(String fromHash, String toHash) throws IOException, IllegalArgumentException {
        ObjectId fromId = repository.resolve(fromHash);
        ObjectId toId = repository.resolve(toHash);

        if (fromId == null || toId == null) {
            throw new IllegalArgumentException("Invalid hash");
        }

        List<GitCommitInfo> commitInfos = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit fromCommit = walk.parseCommit(fromId);

            walk.markStart(walk.parseCommit(toId));
            walk.markUninteresting(fromCommit);

            for (RevCommit rev : walk) {
                commitInfos.add(buildCommitInfo(rev));
            }
        }
        return commitInfos;
    }

    private GitCommitInfo buildCommitInfo(RevCommit rev) throws IOException {
        GitCommitInfo info = new GitCommitInfo();
        info.setCommitHash(rev.getId().getName());
        info.setAuthorName(rev.getAuthorIdent().getName());
        info.setAuthorEmail(rev.getAuthorIdent().getEmailAddress());
        info.setTimestamp(rev.getCommitTime());
        info.setFullMessage(rev.getFullMessage());

        if (rev.getParentCount() > 0) {
            ObjectId parentTree = rev.getParent(0).getTree().getId();
            ObjectId currentTree = rev.getTree().getId();
            info.setDiffEntries(getDiffBetweenTrees(parentTree, currentTree));
        } else {
            info.setDiffEntries(getAllFilesAsAdd(rev.getTree().getId()));
        }
        return info;
    }

    private List<GitDiffEntry> getDiffBetweenTrees(ObjectId fromTree, ObjectId toTree) throws IOException {
        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(repository);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            df.setDetectRenames(true);

            List<DiffEntry> entries = df.scan(fromTree, toTree);
            List<GitDiffEntry> result = new ArrayList<>();
            for (DiffEntry entry : entries) {
                GitDiffEntry dto = new GitDiffEntry();
                dto.setChangeOperation(mapChangeType(entry.getChangeType()));
                dto.setOldPath(DiffEntry.DEV_NULL.equals(entry.getOldPath()) ? null : entry.getOldPath());
                dto.setNewPath(DiffEntry.DEV_NULL.equals(entry.getNewPath()) ? null : entry.getNewPath());

                if (entry.getChangeType() != DiffEntry.ChangeType.DELETE) {
                    dto.setNewContent(readFileContent(toTree, entry.getNewPath()));
                }
                if (entry.getChangeType() != DiffEntry.ChangeType.ADD) {
                    dto.setOldContent(readFileContent(fromTree, entry.getOldPath()));
                }
                result.add(dto);
            }
            return result;
        }
    }

    private @Nullable String getFileContent(String commitHash, String filePath) throws IOException {
        ObjectId commitId = repository.resolve(commitHash);
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(commitId);
            try (org.eclipse.jgit.treewalk.TreeWalk tw = org.eclipse.jgit.treewalk.TreeWalk.forPath(repository, filePath, commit.getTree())) {
                if (tw == null) return null;
                ObjectId blobId = tw.getObjectId(0);
                ObjectLoader loader = repository.open(blobId);
                return new String(loader.getBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private @Nullable String readFileContent(ObjectId treeId, String filePath) throws IOException {
        try (org.eclipse.jgit.treewalk.TreeWalk tw = org.eclipse.jgit.treewalk.TreeWalk.forPath(repository, filePath, treeId)) {
            if (tw == null) return null;
            ObjectId blobId = tw.getObjectId(0);
            ObjectLoader loader = repository.open(blobId);
            return new String(loader.getBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<GitDiffEntry> getAllFilesAsAdd(ObjectId treeId) throws IOException {
        List<GitDiffEntry> result = new ArrayList<>();
        try (org.eclipse.jgit.treewalk.TreeWalk tw = new org.eclipse.jgit.treewalk.TreeWalk(repository)) {
            tw.addTree(treeId);
            tw.setRecursive(true);
            while (tw.next()) {
                GitDiffEntry dto = new GitDiffEntry();
                dto.setChangeOperation(ChangeOperation.ADD);
                dto.setOldPath(null);
                dto.setNewPath(tw.getPathString());
                dto.setOldContent(null);
                ObjectId blobId = tw.getObjectId(0);
                ObjectLoader loader = repository.open(blobId);
                dto.setNewContent(new String(loader.getBytes(), StandardCharsets.UTF_8));
                result.add(dto);
            }
        }
        return result;
    }


    @Override
    public void close() {
        if (repository != null) repository.close();
        if (git != null) git.close();
    }

    private ChangeOperation mapChangeType(DiffEntry.@NonNull ChangeType type) {
        return switch (type) {
            case ADD -> ChangeOperation.ADD;
            case DELETE -> ChangeOperation.REMOVE;
            case MODIFY, COPY, RENAME -> ChangeOperation.MODIFY;
        };
    }
}
