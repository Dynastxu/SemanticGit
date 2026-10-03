package com.github.semanticgit.common.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitMeta {
    private Long id;
    private String hash;
    private Author author;
    private Integer timestamp; // FIXME 改为 Long 类型
    private String message;
    /**
     * First-parent commit (linear history).
     * For the initial commit, this is null.
     */
    private CommitMeta parentCommitMeta;
    /**
     * Second-parent commit for merge commits (the branch being merged in).
     * Null for non-merge commits.
     * For octopus merges with &gt;2 parents, only the first merge parent is captured.
     *
     * @apiNote Callers in the git module must populate this field from
     *          {@code GitCommitInfo.parentHashes.get(1)} when {@code isMergeCommit()} is true.
     */
    private CommitMeta mergeParentMeta;
}
