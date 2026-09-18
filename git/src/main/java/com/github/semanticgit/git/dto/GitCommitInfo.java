package com.github.semanticgit.git.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GitCommitInfo {
    private String commitHash;
    private String authorName;
    private String authorEmail;
    private long timestamp;
    private String fullMessage;
    private List<GitDiffEntry> diffEntries;

    // ---- merge 语义 ----
    /** 父提交数量。0=root，1=普通提交，>=2=merge */
    private int parentCount;
    /** 所有父提交 hash，顺序与 Git 一致（first parent 在最前） */
    private List<String> parentHashes;
    /** 是否 merge commit（parentCount >= 2） */
    private boolean mergeCommit;
    /** diffEntries 相对哪个父提交计算。root 提交为 null */
    private String diffBaseParentHash;
}