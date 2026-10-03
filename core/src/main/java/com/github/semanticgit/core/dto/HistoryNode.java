package com.github.semanticgit.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryNode {
    private String commitHash;
    private String authorName;
    private int timestamp;
    private String shortMessage;
    private boolean mergeCommit;
    private int depth;
    private String branchHint;
}