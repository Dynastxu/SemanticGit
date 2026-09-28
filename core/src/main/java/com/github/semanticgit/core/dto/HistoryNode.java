package com.github.semanticgit.core.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HistoryNode {
    private String commitHash;
    private String authorName;
    private int timestamp;
    private String shortMessage;
    private boolean mergeCommit;
    private int depth;
    private String branchHint;
}
