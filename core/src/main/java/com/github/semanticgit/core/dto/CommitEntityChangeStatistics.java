package com.github.semanticgit.core.dto;

import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.common.entity.ChangeOperation;
import com.github.semanticgit.common.entity.CommitMeta;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class CommitEntityChangeStatistics {
    private CommitMeta commitMeta;
    private Map<ChangeOperation, Float> operationFloatMap;
    private Map<ChangeNatureFlag, Float> natureFlagFloatMap;
}
