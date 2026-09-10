package com.github.semanticgit.parser.java.api;

import com.github.semanticgit.common.entity.DataQuality;
import com.github.semanticgit.common.entity.Entity;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ParsingResult {
    /**
     * 提取到的实体列表（类/方法）
     */
    private List<Entity> entities;

    /**
     * 数据质量：与数据库字段对应
     */
    private DataQuality quality;

    /**
     * 如果质量降级，记录具体原因
     */
    private String qualityRemark;

    /**
     * 解析耗时（毫秒）
     */
    private long parseDurationMs;
}
