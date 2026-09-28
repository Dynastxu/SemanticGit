package com.github.semanticgit.core.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HistoryEdge {
    public enum EdgeType {
        FIRST_PARENT,
        MERGE_PARENT
    }

    private String fromHash;
    private String toHash;
    private EdgeType type;
}
