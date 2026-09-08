package com.github.semanticgit.common.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeLog {
    private Long id;
    private CommitMeta commit;
    private Entity entity;
    private String filePath;
    private ChangeOperation operation;
    private ChangeNatureFlag natureFlag;
    /**
     * 重构时指向旧实体
     */
    private Entity parentEntity;
    private DataQuality dataQuality;
    private AnalysisType analysisType;
}
