package com.github.semanticgit.core.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder()
public class SimpleEntityChangeStatistics {
    @Builder.Default
    private boolean success = true;

    private int totalChanges;
    private int adds;
    private int removes;
    private int modifies;

    private int logical;
    private int refactors;
    private int styles;
    private int docs;

    public static SimpleEntityChangeStatistics fail() {
        return new SimpleEntityChangeStatisticsBuilder().success(false).build();
    }
}
