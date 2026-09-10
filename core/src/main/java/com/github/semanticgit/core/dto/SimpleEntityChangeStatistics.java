package com.github.semanticgit.core.dto;

import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.common.entity.ChangeOperation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class SimpleEntityChangeStatistics {
    @Builder.Default
    private boolean success = true;

    private int totalCommits;

    private Map<ChangeOperation, Float> operationFloatMap;
    private Map<ChangeNatureFlag, Float> natureFlagFloatMap;

    public static SimpleEntityChangeStatistics fail() {
        return new SimpleEntityChangeStatisticsBuilder().success(false).build();
    }
}
