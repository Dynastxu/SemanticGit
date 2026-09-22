package com.github.semanticgit.core.dto;

import com.github.semanticgit.common.entity.Author;
import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.common.entity.ChangeOperation;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Builder
@Getter
public class AuthorChangeStatistics {
    private Author author;
    private Map<ChangeOperation, Float> operationFloatMap;
    private Map<ChangeNatureFlag, Float> natureFlagFloatMap;
}
