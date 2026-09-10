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
    /**
     * 变更性质标志位掩码，支持多标志组合（如 REFACTOR | DOCS）
     *
     * @see ChangeNatureFlag#toCode(java.util.EnumSet)
     * @see ChangeNatureFlag#fromCode(int)
     */
    private int natureFlagCode;
    /**
     * 重构时指向旧实体
     */
    private Entity parentEntity;
    private DataQuality dataQuality;
    private AnalysisType analysisType;
}
